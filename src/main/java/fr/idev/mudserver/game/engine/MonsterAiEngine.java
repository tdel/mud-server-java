package fr.idev.mudserver.game.engine;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.actor.component.CharacterCombat;
import fr.idev.mudserver.domain.actor.event.CharacterDied;
import fr.idev.mudserver.domain.actor.event.MonsterAttacked;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.instance.MonsterInstance;
import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.map.HexDirection;
import fr.idev.mudserver.domain.world.RoomInstance;
import fr.idev.mudserver.network.message.ingame.AttackReceived;
import fr.idev.mudserver.network.message.ingame.MonsterGaveUpChase;
import fr.idev.mudserver.network.message.ingame.MonsterStartedChasing;

@Component
public class MonsterAiEngine {

    public static final int LEASH_RADIUS = 10;

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

        monster.pursuit = new PursuitState(State.CHASING, attacker, 0L, 0L);
        pursuing.put(monster.getId(), monster);

        if (startingChase) {
            log.info("monster.ai.aggro monsterId={} attackerId={}", monster.getId(), attacker.getId());
            attacker.send(new MonsterStartedChasing(monster.getName()));
        }
    }

    public void forget(MonsterInstance monster) {
        pursuing.remove(monster.getId());
        monster.pursuit = null;
    }

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    void tick() {
        long now = System.currentTimeMillis();
        for (MonsterInstance monster : pursuing.values()) {
            PursuitState state = monster.pursuit;
            if (state == null) {
                pursuing.remove(monster.getId());
                continue;
            }

            if (state.state() == State.CHASING) {
                tickChasing(monster, state, now);
            } else {
                tickReturning(monster, state, now);
            }
        }
    }

    private void tickChasing(MonsterInstance monster, PursuitState state, long now) {
        CharacterInstance target = state.target();
        RoomInstance room = monster.getCurrentRoom();

        if (target == null || target.getCurrentHealth() <= 0 || !room.isOccupant(target)) {
            giveUpChase(monster, target);
            return;
        }

        if (monster.getSpawnCell().distanceTo(monster.getPosition()) > LEASH_RADIUS) {
            giveUpChase(monster, target);
            return;
        }

        if (monster.getPosition().distanceTo(target.getPosition()) <= 1) {
            if (now < state.nextAttackAt()) {
                return;
            }
            MonsterInstance.MonsterAttackOutcome outcome = monster.attack(target);
            target.send(new AttackReceived(monster.getName(), outcome.hit(), outcome.critical(), outcome.damage(),
                    outcome.targetHealthAfter(), outcome.targetMaxHealth(), outcome.targetDefeated()));
            monster.pursuit = state.withNextAttackAt(now + CharacterCombat.ATTACK_COOLDOWN.toMillis());
            return;
        }

        if (now - state.lastStepAt() < millisPerCell(monster)) {
            return;
        }
        if (stepToward(monster, target.getPosition())) {
            monster.pursuit = state.withLastStepAt(now);
        }
    }

    private void tickReturning(MonsterInstance monster, PursuitState state, long now) {
        if (monster.getPosition().equals(monster.getSpawnCell())) {
            forget(monster);
            return;
        }

        if (now - state.lastStepAt() < millisPerCell(monster)) {
            return;
        }
        if (stepToward(monster, monster.getSpawnCell())) {
            monster.pursuit = state.withLastStepAt(now);
        } else {
            // Bloqué : on abandonne pour ne pas tourner en rond indéfiniment.
            forget(monster);
        }
    }

    private void giveUpChase(MonsterInstance monster, CharacterInstance target) {
        log.info("monster.ai.give_up monsterId={}", monster.getId());
        monster.pursuit = new PursuitState(State.RETURNING, null, 0L, 0L);
        if (target != null) {
            target.send(new MonsterGaveUpChase(monster.getName()));
        }
    }

    private boolean stepToward(MonsterInstance monster, HexCoordinate destination) {
        RoomInstance room = monster.getCurrentRoom();
        HexCoordinate current = monster.getPosition();
        int bestDistance = current.distanceTo(destination);
        HexDirection bestDirection = null;

        for (HexDirection direction : HexDirection.values()) {
            HexCoordinate candidate = current.neighbor(direction);
            if (!room.isInBounds(candidate)) {
                continue;
            }
            int candidateDistance = candidate.distanceTo(destination);
            if (candidateDistance < bestDistance) {
                bestDistance = candidateDistance;
                bestDirection = direction;
            }
        }

        if (bestDirection == null) {
            return false;
        }

        HexCoordinate next = current.neighbor(bestDirection);
        if (!room.tryClaimCell(next, monster)) {
            return false;
        }
        room.releaseCell(current, monster);
        monster.setPosition(next);
        return true;
    }

    private long millisPerCell(MonsterInstance monster) {
        return MovementEngine.REFERENCE_TIME_MS * MovementEngine.REFERENCE_SPEED / Math.max(1, monster.getSpeed());
    }

    public enum State {
        CHASING, RETURNING
    }

    public record PursuitState(State state, CharacterInstance target, long lastStepAt, long nextAttackAt) {
        PursuitState withLastStepAt(long stepAt) {
            return new PursuitState(state, target, stepAt, nextAttackAt);
        }

        PursuitState withNextAttackAt(long attackAt) {
            return new PursuitState(state, target, lastStepAt, attackAt);
        }
    }
}
