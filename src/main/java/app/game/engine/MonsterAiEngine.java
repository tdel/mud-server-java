package app.game.engine;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import app.domain.actor.event.CharacterDied;
import app.domain.actor.event.MonsterAttacked;
import app.domain.actor.instance.CharacterInstance;
import app.domain.actor.instance.MonsterInstance;
import app.domain.map.Position;
import app.domain.world.CollisionGrid;
import app.domain.world.MapInstance;
import app.domain.world.PeaceZone;
import app.game.combat.CombatFormulas;
import app.network.message.ingame.AttackResult;
import app.network.message.ingame.CharacterMovementFinished;
import app.network.message.ingame.CharacterMovementStarted;
import app.network.message.ingame.CharacterMovementStopped;
import app.network.message.ingame.MonsterGaveUpChase;
import app.network.message.ingame.MonsterStartedChasing;

@Component
public class MonsterAiEngine {

    public static final double LEASH_RADIUS = 10.0;
    public static final double ATTACK_RANGE = 1.0;

    private static final Logger log = LoggerFactory.getLogger(MonsterAiEngine.class);

    private static final long TICK_INTERVAL_MS = 250L;

    private final Map<UUID, MonsterInstance> pursuing = new ConcurrentHashMap<>();

    @EventListener
    void onMonsterAttacked(MonsterAttacked event) {
        aggro(event.monster(), event.attacker());
    }

    @EventListener
    void onCharacterDied(CharacterDied event) {
        forget(event.character());
    }

    public void aggro(MonsterInstance monster, CharacterInstance attacker) {
        PursuitState current = monster.pursuit;
        boolean startingChase = current == null || current.state() != State.CHASING;

        monster.pursuit = new PursuitState(State.CHASING, attacker, System.nanoTime(), 0L, false);
        pursuing.put(monster.getId(), monster);

        if (startingChase) {
            log.info("monster.ai.aggro thread={} monsterId={} attackerId={}", Thread.currentThread().getName(),
                    monster.getId(), attacker.getId());
            attacker.send(new MonsterStartedChasing(monster.getName()));
        }
    }

    public void forget(MonsterInstance monster) {
        pursuing.remove(monster.getId());
        monster.pursuit = null;
    }

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    void tick() {
        long nowMillis = System.currentTimeMillis();
        long nowNanos = System.nanoTime();
        for (MonsterInstance monster : pursuing.values()) {
            PursuitState state = monster.pursuit;
            if (state == null) {
                pursuing.remove(monster.getId());
                continue;
            }

            if (state.state() == State.CHASING) {
                tickChasing(monster, state, nowMillis, nowNanos);
            } else {
                tickReturning(monster, state, nowNanos);
            }
        }
    }

    private void tickChasing(MonsterInstance monster, PursuitState state, long nowMillis, long nowNanos) {
        CharacterInstance target = state.target();
        MapInstance map = monster.getMotionSystem().getCurrentMap();

        if (target == null || target.getCurrentHealth() <= 0 || !map.isPresent(target)) {
            giveUpChase(monster, state, target);
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
                .distanceTo(target.getMotionSystem().getPosition()) <= ATTACK_RANGE) {
            if (state.moving()) {
                // À portée : le monstre s'arrête de courir pour attaquer, comme un joueur
                // (voir Attack.java) — sans cette diffusion, le client continuerait
                // d'interpoler le monstre vers la dernière position connue de la cible.
                monster.broadcast(new CharacterMovementStopped(monster.getId(), monster.getName(),
                        monster.getMotionSystem().getPosition().x(), monster.getMotionSystem().getPosition().y()),
                        null);
                state = state.withMoving(false);
                monster.pursuit = state;
            }
            if (nowMillis < state.nextAttackAt()) {
                return;
            }
            MonsterInstance.MonsterAttackOutcome outcome = monster.attack(target);
            monster.broadcast(new AttackResult(monster.getId(), monster.getName(), target.getId(), target.getName(),
                    outcome.hit(), outcome.critical(), outcome.damage(), outcome.targetHealthAfter()), null);
            monster.pursuit = state.withNextAttackAt(
                    nowMillis + CombatFormulas.attackCooldown(monster.getEffectiveAtkSpd()).toMillis());
            return;
        }

        double dtSeconds = (nowNanos - state.lastStepAtNanos()) / 1_000_000_000.0;
        if (stepToward(monster, target.getMotionSystem().getPosition(), dtSeconds)) {
            // Rediffusé à chaque pas (toutes les ~250ms, voir TICK_INTERVAL_MS) plutôt
            // qu'une seule fois au début de la poursuite : contrairement à un `goto`
            // joueur, la destination (la cible) bouge en continu, donc le client doit
            // recevoir une cible d'interpolation fraîche à chaque tick pour ne pas
            // diverger.
            monster.broadcast(new CharacterMovementStarted(monster.getId(), monster.getName(),
                    target.getMotionSystem().getPosition().x(), target.getMotionSystem().getPosition().y(),
                    monster.getMotionSystem().getHeading()), null);
            monster.pursuit = state.withLastStepAt(nowNanos).withMoving(true);
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

        double dtSeconds = (nowNanos - state.lastStepAtNanos()) / 1_000_000_000.0;
        if (stepToward(monster, monster.getSpawnPosition(), dtSeconds)) {
            monster.broadcast(
                    new CharacterMovementStarted(monster.getId(), monster.getName(), monster.getSpawnPosition().x(),
                            monster.getSpawnPosition().y(), monster.getMotionSystem().getHeading()),
                    null);
            monster.pursuit = state.withLastStepAt(nowNanos).withMoving(true);
        } else {
            // Bloqué : on abandonne pour ne pas tourner en rond indéfiniment.
            if (state.moving()) {
                monster.broadcast(new CharacterMovementStopped(monster.getId(), monster.getName(),
                        monster.getMotionSystem().getPosition().x(), monster.getMotionSystem().getPosition().y()),
                        null);
            }
            forget(monster);
        }
    }

    private void giveUpChase(MonsterInstance monster, PursuitState state, CharacterInstance target) {
        log.info("monster.ai.give_up thread={} monsterId={}", Thread.currentThread().getName(), monster.getId());
        if (state.moving()) {
            monster.broadcast(
                    new CharacterMovementStopped(monster.getId(), monster.getName(),
                            monster.getMotionSystem().getPosition().x(), monster.getMotionSystem().getPosition().y()),
                    null);
        }
        monster.pursuit = new PursuitState(State.RETURNING, null, System.nanoTime(), 0L, false);
        if (target != null) {
            target.send(new MonsterGaveUpChase(monster.getName()));
        }
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

    public record PursuitState(State state, CharacterInstance target, long lastStepAtNanos, long nextAttackAt,
            boolean moving) {
        PursuitState withLastStepAt(long stepAtNanos) {
            return new PursuitState(state, target, stepAtNanos, nextAttackAt, moving);
        }

        PursuitState withNextAttackAt(long attackAt) {
            return new PursuitState(state, target, lastStepAtNanos, attackAt, moving);
        }

        PursuitState withMoving(boolean moving) {
            return new PursuitState(state, target, lastStepAtNanos, nextAttackAt, moving);
        }
    }
}
