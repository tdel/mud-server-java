package fr.idev.mudserver.network.command.ingame;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.component.CharacterCombat;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.network.CommandHandler;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.ingame.AttackOnCooldown;
import fr.idev.mudserver.network.message.ingame.AttackOutOfRange;
import fr.idev.mudserver.network.message.ingame.AttackReceived;
import fr.idev.mudserver.network.message.ingame.AttackResult;
import fr.idev.mudserver.network.message.ingame.NoTargetSelected;
import fr.idev.mudserver.network.message.ingame.TargetNotFound;

@Component
public class Attack implements CommandHandler {

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
            if (!character.getCurrentRoom().isOccupant(target)) {
                combat.setTarget(null);
                connection.send(new TargetNotFound(target.getName()));
                return;
            }
        } else {
            Optional<AbstractCharacter> found = character.getCurrentRoom().findAttackableByName(name, character);
            if (found.isEmpty()) {
                connection.send(new TargetNotFound(name));
                return;
            }
            target = found.get();
            combat.setTarget(target);
        }

        if (character.getPosition().distanceTo(target.getPosition()) > 1) {
            connection.send(new AttackOutOfRange(target.getName()));
            return;
        }

        if (!combat.isReady()) {
            connection.send(new AttackOnCooldown(combat.remainingCooldown().toMillis()));
            return;
        }

        CharacterCombat.AttackOutcome outcome = combat.attack(target);

        connection.send(new AttackResult(target.getName(), outcome.hit(), outcome.critical(), outcome.damage(),
                outcome.targetHealthAfter(), outcome.targetMaxHealth(), outcome.targetDefeated()));
        target.send(new AttackReceived(character.getName(), outcome.hit(), outcome.critical(), outcome.damage(),
                outcome.targetHealthAfter(), outcome.targetMaxHealth(), outcome.targetDefeated()));

        if (outcome.targetDefeated()) {
            combat.setTarget(null);
        }
    }
}
