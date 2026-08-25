package app.network.command.ingame;

import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import app.domain.actor.AbstractCharacter;
import app.domain.actor.component.CharacterCombat;
import app.domain.actor.instance.CharacterInstance;
import app.game.engine.MonsterAiEngine;
import app.network.CommandHandler;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.ingame.AttackObserved;
import app.network.message.ingame.AttackOnCooldown;
import app.network.message.ingame.AttackOutOfRange;
import app.network.message.ingame.AttackReceived;
import app.network.message.ingame.AttackResult;
import app.network.message.ingame.NoTargetSelected;
import app.network.message.ingame.TargetNotFound;

@Component
public class Attack implements CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(Attack.class);

    @Override
    public String name() {
        return "attack";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();
        CharacterCombat combat = character.getCombat();
        String name = argument.trim();

        AbstractCharacter target;
        if (name.isEmpty()) {
            target = combat.getTarget();
            if (target == null) {
                connection.send(new NoTargetSelected());
                return;
            }
            if (!character.getCurrentZone().isPresent(target)) {
                combat.setTarget(null);
                connection.send(new TargetNotFound(target.getName()));
                return;
            }
        } else {
            Optional<AbstractCharacter> found = character.getCurrentZone().findAttackableByName(name, character);
            if (found.isEmpty()) {
                log.debug("attack.rejected character={} reason=target_not_found target={}", character.getId(), name);
                connection.send(new TargetNotFound(name));
                return;
            }
            target = found.get();
            combat.setTarget(target);
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

        CharacterCombat.AttackOutcome outcome = combat.attack(target);

        connection.send(new AttackResult(target.getName(), outcome.hit(), outcome.critical(), outcome.damage(),
                outcome.targetHealthAfter(), outcome.targetMaxHealth(), outcome.targetDefeated()));
        target.send(new AttackReceived(character.getName(), outcome.hit(), outcome.critical(), outcome.damage(),
                outcome.targetHealthAfter(), outcome.targetMaxHealth(), outcome.targetDefeated()));
        character.getCurrentZone().broadcast(new AttackObserved(character.getName(), target.getName(), outcome.hit(),
                outcome.critical(), outcome.damage(), outcome.targetDefeated()), null);

        if (outcome.targetDefeated()) {
            combat.setTarget(null);
        }
    }
}
