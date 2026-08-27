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

import app.domain.actor.component.CharacterCombat;
import app.domain.actor.event.CharacterDied;
import app.domain.actor.event.MonsterAttacked;
import app.domain.actor.instance.CharacterInstance;
import app.domain.actor.instance.MonsterInstance;
import app.domain.map.Position;
import app.domain.world.CollisionGrid;
import app.domain.world.ZoneInstance;
import app.network.message.ingame.AttackObserved;
import app.network.message.ingame.AttackReceived;
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

        monster.pursuit = new PursuitState(State.CHASING, attacker, System.nanoTime(), 0L);
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
        ZoneInstance zone = monster.getCurrentZone();

        if (target == null || target.getCurrentHealth() <= 0 || !zone.isPresent(target)) {
            giveUpChase(monster, target);
            return;
        }

        if (monster.getSpawnPosition().distanceTo(monster.getPosition()) > LEASH_RADIUS) {
            giveUpChase(monster, target);
            return;
        }

        if (monster.getPosition().distanceTo(target.getPosition()) <= ATTACK_RANGE) {
            if (nowMillis < state.nextAttackAt()) {
                return;
            }
            MonsterInstance.MonsterAttackOutcome outcome = monster.attack(target);
            target.send(new AttackReceived(monster.getId(), monster.getName(), outcome.hit(), outcome.critical(),
                    outcome.damage(), outcome.targetHealthAfter(), outcome.targetMaxHealth(),
                    outcome.targetDefeated()));
            zone.broadcast(new AttackObserved(monster.getId(), monster.getName(), target.getId(), target.getName(),
                    outcome.hit(), outcome.critical(), outcome.damage(), outcome.targetDefeated()), null);
            monster.pursuit = state.withNextAttackAt(nowMillis + CharacterCombat.ATTACK_COOLDOWN.toMillis());
            return;
        }

        double dtSeconds = (nowNanos - state.lastStepAtNanos()) / 1_000_000_000.0;
        if (stepToward(monster, target.getPosition(), dtSeconds)) {
            monster.pursuit = state.withLastStepAt(nowNanos);
        }
    }

    private void tickReturning(MonsterInstance monster, PursuitState state, long nowNanos) {
        if (monster.getPosition().distanceTo(monster.getSpawnPosition()) < 1e-6) {
            forget(monster);
            return;
        }

        double dtSeconds = (nowNanos - state.lastStepAtNanos()) / 1_000_000_000.0;
        if (stepToward(monster, monster.getSpawnPosition(), dtSeconds)) {
            monster.pursuit = state.withLastStepAt(nowNanos);
        } else {
            // Bloqué : on abandonne pour ne pas tourner en rond indéfiniment.
            forget(monster);
        }
    }

    private void giveUpChase(MonsterInstance monster, CharacterInstance target) {
        log.info("monster.ai.give_up thread={} monsterId={}", Thread.currentThread().getName(), monster.getId());
        monster.pursuit = new PursuitState(State.RETURNING, null, System.nanoTime(), 0L);
        if (target != null) {
            target.send(new MonsterGaveUpChase(monster.getName()));
        }
    }

    private boolean stepToward(MonsterInstance monster, Position destination, double dtSeconds) {
        ZoneInstance zone = monster.getCurrentZone();
        CollisionGrid grid = zone.getCollisionGrid();
        ContinuousStep.StepResult result = ContinuousStep.step(monster.getPosition(), List.of(destination),
                MovementEngine.unitsPerSecond(monster.getSpeed()), dtSeconds, grid);
        monster.setPosition(result.position());
        return !result.blocked();
    }

    public enum State {
        CHASING, RETURNING
    }

    public record PursuitState(State state, CharacterInstance target, long lastStepAtNanos, long nextAttackAt) {
        PursuitState withLastStepAt(long stepAtNanos) {
            return new PursuitState(state, target, stepAtNanos, nextAttackAt);
        }

        PursuitState withNextAttackAt(long attackAt) {
            return new PursuitState(state, target, lastStepAtNanos, attackAt);
        }
    }
}
