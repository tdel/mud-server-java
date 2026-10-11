package app.game.engine;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import app.domain.AbnormalStatus;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.AbstractNpc;
import app.domain.actor.event.CharacterBeginAttack;
import app.domain.actor.event.CharacterDied;
import app.domain.actor.instance.MonsterInstance;
import app.domain.actor.instance.PlayerInstance;
import app.domain.actor.instance.SummonInstance;
import app.domain.map.Position;
import app.domain.world.CollisionGrid;
import app.domain.world.MapInstance;
import app.domain.world.PeaceZone;
import app.game.WorldInstanceService;
import app.game.combat.CombatFormulas;
import app.network.message.ingame.CharacterMovementFinished;
import app.network.message.ingame.CharacterMovementStarted;
import app.network.message.ingame.CharacterMovementStopped;
import app.network.message.ingame.MonsterGaveUpChase;
import app.network.message.ingame.MonsterStartedChasing;

// IA des monstres : agression des joueurs à portée pour les monstres agressifs
// (voir scanAggro), poursuite de celui qui les attaque (joueur ou serviteur),
// retour au point d'apparition, et altérations d'état des compétences de
// profession — étourdi/endormi/paralysé : inactif ; enraciné : frappe sans
// bouger ; peur : fuit sa cible ; confusion : s'en prend à un voisin au hasard ;
// passif (Requiem) ou cible en mort feinte : abandonne le combat.
@Component
public class MonsterAiEngine {

    public static final double LEASH_RADIUS = 10.0;

    private static final Logger log = LoggerFactory.getLogger(MonsterAiEngine.class);

    private static final long TICK_INTERVAL_MS = 250L;
    // Curse Discord : rayon dans lequel un monstre confus choisit sa victime.
    private static final double CONFUSION_RADIUS = 10.0;

    // Monstres agressifs (aggroRadius > 0 dans data/monsters.xml, isAggressive des
    // PNJ L2) : fréquence du balayage des joueurs à portée.
    private static final long AGGRO_SCAN_INTERVAL_MS = 500L;

    // Diffusion des déplacements : un CharacterMovementStarted n'est envoyé qu'au
    // départ, à l'arrêt et quand la destination annoncée n'est plus la bonne — le
    // client extrapole la course entre deux à la vitesse de l'EntityView. En
    // poursuite, la destination annoncée est la position de la cible : on ne la
    // réannonce que lorsque la cible s'en est écartée de plus de cette distance
    // (inférieure à ATTACK_RANGE, pour qu'un monstre arrivé au point annoncé soit
    // à portée de frappe).
    private static final double CHASE_REANNOUNCE_DISTANCE = 1.5;
    // Peur : point de fuite visé (à cette distance) et renouvelé quand le monstre
    // en approche à moins de FLEE_REACHED_DISTANCE.
    private static final double FLEE_DISTANCE = 4.0;
    private static final double FLEE_REACHED_DISTANCE = 1.0;

    private final Map<UUID, MonsterInstance> pursuing = new ConcurrentHashMap<>();
    private final ObjectProvider<WorldInstanceService> worldInstanceService;

    public MonsterAiEngine(ObjectProvider<WorldInstanceService> worldInstanceService) {
        this.worldInstanceService = worldInstanceService;
    }

    @EventListener
    void onCharacterBeginAttack(CharacterBeginAttack event) {
        if (!(event.defender() instanceof MonsterInstance monster)) {
            return;
        }
        if (event.attacker() instanceof PlayerInstance || event.attacker() instanceof SummonInstance) {
            aggro(monster, event.attacker());
        }
    }

    @EventListener
    void onCharacterDied(CharacterDied event) {
        if (event.character() instanceof MonsterInstance monster) {
            forget(monster);
        }
    }

    // Le monstre prend `attacker` pour cible (attaque reçue, Aggression, Aura of
    // Hate) — sauf s'il est apaisé (Requiem) ou que la cible feint la mort.
    public void aggro(MonsterInstance monster, AbstractCharacter attacker) {
        if (monster.getEffectsSystem().hasStatus(AbnormalStatus.PASSIVE)
                || attacker.getEffectsSystem().hasStatus(AbnormalStatus.FAKE_DEATH)) {
            return;
        }
        PursuitState current = monster.pursuit;
        boolean startingChase = current == null || current.state() != State.CHASING || current.target() != attacker;

        monster.pursuit = new PursuitState(State.CHASING, attacker, System.nanoTime(),
                current != null ? current.destination() : null);
        pursuing.put(monster.getId(), monster);

        if (startingChase) {
            log.info("monster.ai.aggro thread={} monsterId={} attackerId={}", Thread.currentThread().getName(),
                    monster.getId(), attacker.getId());
            attacker.send(new MonsterStartedChasing(monster.getName()));
        }
    }

    // Peace : le monstre cesse le combat et rentre ; false s'il ne combattait
    // pas.
    public boolean calm(MonsterInstance monster) {
        PursuitState state = monster.pursuit;
        if (state == null || state.state() != State.CHASING) {
            return false;
        }
        giveUpChase(monster, state, state.target());
        return true;
    }

    // Tous les monstres qui poursuivent `character` l'abandonnent (Fake Death),
    // sauf `except` (Shadow Step : la victime le retrouve aussitôt dans son dos).
    public void dropTarget(AbstractCharacter character, AbstractCharacter except) {
        for (MonsterInstance monster : pursuing.values()) {
            PursuitState state = monster.pursuit;
            if (monster != except && state != null && state.state() == State.CHASING && state.target() == character) {
                giveUpChase(monster, state, character);
            }
        }
    }

    public void forget(MonsterInstance monster) {
        pursuing.remove(monster.getId());
        monster.pursuit = null;
    }

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    void tick() {
        long nowNanos = System.nanoTime();
        for (MonsterInstance monster : pursuing.values()) {
            PursuitState state = monster.pursuit;
            if (state == null) {
                pursuing.remove(monster.getId());
                continue;
            }
            try {
                if (monster.getEffectsSystem().isIncapacitated()
                        && !monster.getEffectsSystem().hasStatus(AbnormalStatus.FEAR)) {
                    holdStill(monster, state);
                } else if (state.state() == State.CHASING) {
                    tickChasing(monster, state, nowNanos);
                } else {
                    tickReturning(monster, state, nowNanos);
                }
            } catch (Exception e) {
                log.error("monster.ai.tick_failed monsterId={}", monster.getId(), e);
            }
        }
    }

    // Agression façon L2 : un monstre agressif au repos (ni en poursuite ni sur le
    // retour) attaque le joueur vivant le plus proche entré dans son rayon
    // d'aggro (en cases), hors zone de paix et sauf mort feinte. Les monstres
    // passifs (aggroRadius 0) ne répondent qu'aux coups.
    @Scheduled(fixedRate = AGGRO_SCAN_INTERVAL_MS)
    void scanAggro() {
        WorldInstanceService worlds = worldInstanceService.getIfAvailable();
        if (worlds == null || !worlds.isDefaultWorldMaterialized()) {
            return;
        }
        for (MapInstance map : worlds.getDefaultInstance().mapInstances()) {
            List<PlayerInstance> players = map.characters();
            if (players.isEmpty()) {
                continue;
            }
            for (MonsterInstance monster : map.getMonsters()) {
                try {
                    scanAggro(monster, players);
                } catch (Exception e) {
                    log.error("monster.ai.aggro_scan_failed monsterId={}", monster.getId(), e);
                }
            }
        }
    }

    private void scanAggro(MonsterInstance monster, List<PlayerInstance> players) {
        if (monster.getAggroRadius() <= 0 || monster.pursuit != null
                || monster.getResourceSystem().getCurrentHealth() <= 0 || monster.getZone() instanceof PeaceZone
                || monster.getEffectsSystem().hasStatus(AbnormalStatus.PASSIVE)
                || monster.getEffectsSystem().isIncapacitated()) {
            return;
        }
        Position position = monster.getMotionSystem().getPosition();
        PlayerInstance nearest = null;
        double nearestDistance = monster.getAggroRadius();
        for (PlayerInstance player : players) {
            if (player.getResourceSystem().getCurrentHealth() <= 0 || player.getZone() instanceof PeaceZone
                    || player.getEffectsSystem().hasStatus(AbnormalStatus.FAKE_DEATH)) {
                continue;
            }
            double distance = position.distanceTo(player.getMotionSystem().getPosition());
            if (distance <= nearestDistance) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        if (nearest != null) {
            aggro(monster, nearest);
        }
    }

    // Étourdi, endormi ou paralysé : le monstre s'arrête net et ne fait rien.
    private void holdStill(MonsterInstance monster, PursuitState state) {
        if (state.moving()) {
            broadcastStop(monster);
            monster.pursuit = state.withDestination(null);
        }
        monster.pursuit = monster.pursuit.withLastStepAt(System.nanoTime());
    }

    private void tickChasing(MonsterInstance monster, PursuitState state, long nowNanos) {
        if (monster.getEffectsSystem().hasStatus(AbnormalStatus.PASSIVE)) {
            giveUpChase(monster, state, state.target());
            return;
        }
        if (monster.getEffectsSystem().hasStatus(AbnormalStatus.CONFUSION)) {
            state = confusedTarget(monster, state);
        }
        AbstractCharacter target = state.target();
        MapInstance map = monster.getMotionSystem().getCurrentMap();

        if (target == null || target.getResourceSystem().getCurrentHealth() <= 0 || !map.isPresent(target)
                || target.getEffectsSystem().hasStatus(AbnormalStatus.FAKE_DEATH)) {
            giveUpChase(monster, state, target);
            return;
        }

        if (monster.getEffectsSystem().hasStatus(AbnormalStatus.FEAR)) {
            flee(monster, state, target, nowNanos);
            return;
        }

        if (monster.getSpawnPosition().distanceTo(monster.getMotionSystem().getPosition()) > LEASH_RADIUS) {
            giveUpChase(monster, state, target);
            return;
        }

        if (monster.getZone() instanceof PeaceZone || target.getZone() instanceof PeaceZone) {
            giveUpChase(monster, state, target);
            return;
        }

        if (monster.getMotionSystem().getPosition()
                .distanceTo(target.getMotionSystem().getPosition()) <= CombatFormulas.ATTACK_RANGE) {
            if (state.moving()) {
                // À portée : le monstre s'arrête de courir pour attaquer, comme un joueur
                // (voir Attack.java) — sans cette diffusion, le client continuerait
                // d'interpoler le monstre vers la dernière position connue de la cible.
                broadcastStop(monster);
                state = state.withDestination(null);
                monster.pursuit = state;
            }
            // Le cooldown est géré en interne par CombatSystem (rejeté silencieusement
            // via AttackOutcome.OnCooldown tant qu'il n'est pas écoulé) : pas de vérif
            // manuelle nécessaire ici.
            monster.getCombatSystem().attack(target);
            return;
        }

        // Enraciné (Dryad Root) : ne peut plus rejoindre sa cible.
        if (!monster.getEffectsSystem().canMove()) {
            holdStill(monster, state);
            return;
        }

        Position targetPosition = target.getMotionSystem().getPosition();
        double dtSeconds = (nowNanos - state.lastStepAtNanos()) / 1_000_000_000.0;
        if (stepToward(monster, targetPosition, dtSeconds)) {
            // Le serveur suit la cible en continu, mais n'annonce une nouvelle
            // destination que si la cible s'est éloignée du point annoncé : une cible
            // immobile ne coûte qu'un message pour toute la course, une cible en fuite
            // un message toutes les CHASE_REANNOUNCE_DISTANCE cases.
            Position announced = state.destination();
            if (announced == null || announced.distanceTo(targetPosition) > CHASE_REANNOUNCE_DISTANCE) {
                broadcastMovement(monster, targetPosition);
                announced = targetPosition;
            }
            monster.pursuit = state.withLastStepAt(nowNanos).withDestination(announced);
        } else if (state.moving()) {
            // Bloqué contre un obstacle : sans cet arrêt, le client continuerait de
            // courir vers la destination annoncée.
            broadcastStop(monster);
            monster.pursuit = state.withDestination(null);
        }
    }

    // Curse Discord : de temps à autre, le monstre confus change de victime pour
    // un autre occupant vivant à proximité (monstre compris).
    private PursuitState confusedTarget(MonsterInstance monster, PursuitState state) {
        if (state.target() != null && ThreadLocalRandom.current().nextDouble() > 0.15) {
            return state;
        }
        List<AbstractCharacter> candidates = monster.getMotionSystem().getCurrentMap()
                .occupantsWithin(monster.getMotionSystem().getPosition(), CONFUSION_RADIUS).stream()
                .filter(other -> other != monster && !(other instanceof AbstractNpc)
                        && other.getResourceSystem().getCurrentHealth() > 0)
                .toList();
        if (candidates.isEmpty()) {
            return state;
        }
        AbstractCharacter victim = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        PursuitState confused = new PursuitState(State.CHASING, victim, state.lastStepAtNanos(), state.destination());
        monster.pursuit = confused;
        return confused;
    }

    // Curse Fear : le monstre court droit devant lui, à l'opposé de sa cible. Le
    // point de fuite est gardé (et annoncé une seule fois) tant qu'il n'est pas
    // presque atteint et qu'il éloigne toujours de la cible.
    private void flee(MonsterInstance monster, PursuitState state, AbstractCharacter from, long nowNanos) {
        Position position = monster.getMotionSystem().getPosition();
        Position fromPosition = from.getMotionSystem().getPosition();
        Position destination = state.destination();
        boolean keep = destination != null && position.distanceTo(destination) > FLEE_REACHED_DISTANCE
                && destination.distanceTo(fromPosition) > position.distanceTo(fromPosition);
        if (!keep) {
            Position away = position.minus(fromPosition).normalized();
            if (away.length() == 0) {
                away = new Position(1, 0);
            }
            destination = position.plus(away.scaled(FLEE_DISTANCE));
        }
        double dtSeconds = (nowNanos - state.lastStepAtNanos()) / 1_000_000_000.0;
        if (stepToward(monster, destination, dtSeconds)) {
            if (!keep) {
                broadcastMovement(monster, destination);
            }
            monster.pursuit = state.withLastStepAt(nowNanos).withDestination(destination);
        } else {
            holdStill(monster, state);
        }
    }

    private void tickReturning(MonsterInstance monster, PursuitState state, long nowNanos) {
        if (monster.getMotionSystem().getPosition().distanceTo(monster.getSpawnPosition()) < 1e-6) {
            if (state.moving()) {
                monster.broadcast(new CharacterMovementFinished(monster.getId(), monster.getName(),
                        monster.getMotionSystem().getPosition().x(), monster.getMotionSystem().getPosition().y()),
                        null);
            }
            forget(monster);
            return;
        }
        if (!monster.getEffectsSystem().canMove()) {
            holdStill(monster, state);
            return;
        }

        double dtSeconds = (nowNanos - state.lastStepAtNanos()) / 1_000_000_000.0;
        if (stepToward(monster, monster.getSpawnPosition(), dtSeconds)) {
            // Destination fixe : annoncée une seule fois, le client mène la course
            // jusqu'au CharacterMovementFinished.
            if (!monster.getSpawnPosition().equals(state.destination())) {
                broadcastMovement(monster, monster.getSpawnPosition());
            }
            monster.pursuit = state.withLastStepAt(nowNanos).withDestination(monster.getSpawnPosition());
        } else {
            // Bloqué : on abandonne pour ne pas tourner en rond indéfiniment.
            if (state.moving()) {
                broadcastStop(monster);
            }
            forget(monster);
        }
    }

    private void giveUpChase(MonsterInstance monster, PursuitState state, AbstractCharacter target) {
        log.info("monster.ai.give_up thread={} monsterId={}", Thread.currentThread().getName(), monster.getId());
        if (state.moving()) {
            broadcastStop(monster);
        }
        monster.pursuit = new PursuitState(State.RETURNING, null, System.nanoTime(), null);
        pursuing.put(monster.getId(), monster);
        if (target != null) {
            target.send(new MonsterGaveUpChase(monster.getName()));
        }
    }

    private static void broadcastStop(MonsterInstance monster) {
        monster.broadcast(
                new CharacterMovementStopped(monster.getId(), monster.getName(),
                        monster.getMotionSystem().getPosition().x(), monster.getMotionSystem().getPosition().y()),
                null);
    }

    /**
     * Position actuelle du monstre jointe à sa destination : le client recale en
     * douceur sa propre interpolation à chaque pas au lieu de le téléporter à
     * l'arrêt.
     */
    private void broadcastMovement(MonsterInstance monster, Position destination) {
        Position position = monster.getMotionSystem().getPosition();
        monster.broadcast(
                new CharacterMovementStarted(monster.getId(), monster.getName(), position.x(), position.y(),
                        destination.x(), destination.y(), monster.getMotionSystem().getHeading(), List.of(destination)),
                null);
    }

    private boolean stepToward(MonsterInstance monster, Position destination, double dtSeconds) {
        MapInstance map = monster.getMotionSystem().getCurrentMap();
        CollisionGrid grid = map.getCollisionGrid();
        Position previous = monster.getMotionSystem().getPosition();
        ContinuousStep.StepResult result = ContinuousStep.step(previous, List.of(destination),
                MovementEngine.unitsPerSecond(monster.getMotionSystem().getSpeed()), dtSeconds, grid);
        monster.getMotionSystem().setPosition(result.position());
        if (!result.position().equals(previous)) {
            monster.getMotionSystem().setHeading(previous.headingTo(result.position()));
        }
        monster.getKnownList().refresh();
        return !result.blocked();
    }

    public enum State {
        CHASING, RETURNING
    }

    // `destination` : dernier point annoncé aux clients (CharacterMovementStarted),
    // null quand le monstre est à l'arrêt.
    public record PursuitState(State state, AbstractCharacter target, long lastStepAtNanos, Position destination) {
        public boolean moving() {
            return destination != null;
        }

        PursuitState withLastStepAt(long stepAtNanos) {
            return new PursuitState(state, target, stepAtNanos, destination);
        }

        PursuitState withDestination(Position destination) {
            return new PursuitState(state, target, lastStepAtNanos, destination);
        }
    }
}
