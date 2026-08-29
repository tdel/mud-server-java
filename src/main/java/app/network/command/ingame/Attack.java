package app.network.command.ingame;

import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import app.domain.actor.AbstractCharacter;
import app.domain.actor.AbstractNpc;
import app.domain.actor.component.CharacterCombat;
import app.domain.actor.instance.CharacterInstance;
import app.game.engine.MonsterAiEngine;
import app.game.engine.MovementEngine;
import app.network.CommandArguments;
import app.network.CommandHandler;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.ingame.AlreadyCasting;
import app.network.message.ingame.AttackObserved;
import app.network.message.ingame.AttackOnCooldown;
import app.network.message.ingame.AttackOutOfRange;
import app.network.message.ingame.AttackReceived;
import app.network.message.ingame.AttackResult;
import app.network.message.ingame.CharacterMovementStopped;
import app.network.message.ingame.MovementStopped;
import app.network.message.ingame.NoTargetSelected;
import app.network.message.ingame.TargetNotFound;

@Component
public class Attack implements CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(Attack.class);

    private final MovementEngine movementEngine;

    public Attack(MovementEngine movementEngine) {
        this.movementEngine = movementEngine;
    }

    @Override
    public String name() {
        return "attack";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public boolean requiresAlive() {
        return true;
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();

        if (character.isCasting()) {
            connection.send(new AlreadyCasting());
            return;
        }

        CharacterCombat combat = character.getCombat();
        String raw = argument.trim();

        AbstractCharacter target;
        if (raw.isEmpty()) {
            target = combat.getTarget();
            if (target == null) {
                connection.send(new NoTargetSelected());
                return;
            }
            if (!character.getCurrentZone().isPresent(target)) {
                combat.setTarget(null);
                connection.send(new TargetNotFound(target.getId().toString()));
                return;
            }
        } else {
            Optional<AbstractCharacter> found = CommandArguments.tryParseUuid(raw)
                    .flatMap(id -> character.getCurrentZone().findAttackableById(id, character));
            if (found.isEmpty()) {
                log.debug("attack.rejected character={} reason=target_not_found target={}", character.getId(), raw);
                connection.send(new TargetNotFound(raw));
                return;
            }
            target = found.get();
            combat.setTarget(target);
        }

        if (target.getCurrentHealth() <= 0) {
            log.debug("attack.rejected character={} reason=target_dead target={}", character.getId(), target.getId());
            combat.setTarget(null);
            connection.send(new TargetNotFound(target.getId().toString()));
            return;
        }

        // "select" (voir Select.java) permet aussi de cibler un PNJ (interaction, pas
        // combat) : un PNJ sélectionné puis attaqué via une commande "attack" sans nom
        // finirait ici avec une cible non attaquable (CharacterCombat.attack ne gère
        // que
        // MonsterInstance/CharacterInstance).
        if (target instanceof AbstractNpc) {
            log.debug("attack.rejected character={} reason=target_not_attackable target={}", character.getId(),
                    target.getId());
            connection.send(new TargetNotFound(target.getId().toString()));
            return;
        }

        if (character.getPosition().distanceTo(target.getPosition()) > MonsterAiEngine.ATTACK_RANGE) {
            log.debug("attack.rejected character={} reason=out_of_range target={}", character.getId(), target.getId());
            connection.send(new AttackOutOfRange(target.getName()));
            return;
        }

        if (!combat.isReady()) {
            log.debug("attack.rejected character={} reason=on_cooldown target={}", character.getId(), target.getId());
            connection.send(new AttackOnCooldown(combat.remainingCooldown().toMillis()));
            return;
        }

        // Toutes les vérifications (cible, portée, cooldown) sont passées : l'attaque
        // va
        // effectivement avoir lieu, on arrête donc le déplacement en cours juste avant
        // de
        // la calculer — on ne combat pas en marchant.
        if (character.activeMovement != null) {
            movementEngine.stopMovement(character);
            connection.send(new MovementStopped(character.getPosition().x(), character.getPosition().y()));
            character.getCurrentZone().broadcast(new CharacterMovementStopped(character.getId(), character.getName(),
                    character.getPosition().x(), character.getPosition().y()), character);
        }

        CharacterCombat.AttackOutcome outcome = combat.attack(target);

        connection.send(new AttackResult(target.getId(), target.getName(), outcome.hit(), outcome.critical(),
                outcome.damage(), outcome.targetHealthAfter(), outcome.targetMaxHealth(), outcome.targetDefeated()));
        target.send(new AttackReceived(character.getId(), character.getName(), outcome.hit(), outcome.critical(),
                outcome.damage(), outcome.targetHealthAfter(), outcome.targetMaxHealth(), outcome.targetDefeated()));
        character.getCurrentZone().broadcast(new AttackObserved(character.getId(), character.getName(), target.getId(),
                target.getName(), outcome.hit(), outcome.critical(), outcome.damage(), outcome.targetDefeated()), null);

    }
}
