package app.network.command.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import app.domain.actor.system.CombatSystem;
import app.domain.actor.instance.CharacterInstance;
import app.network.CommandHandler;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.ingame.AttackOnCooldown;
import app.network.message.ingame.AttackOutOfRange;
import app.network.message.ingame.CombatForbiddenHere;
import app.network.message.ingame.NoTargetSelected;
import app.network.message.ingame.TargetNotFound;

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
    public boolean requiresAlive() {
        return true;
    }

    @Override
    public boolean requiresNotCasting() {
        return true;
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();
        switch (character.getCombatSystem().attack(character.getCombatSystem().getTarget())) {
            case CombatSystem.AttackOutcome.Success(var cooldownMs) ->
                connection.send(new AttackOnCooldown(cooldownMs));
            case CombatSystem.AttackOutcome.NoTarget ignored -> connection.send(new NoTargetSelected());
            case CombatSystem.AttackOutcome.TargetInvalid(var targetId) ->
                connection.send(new TargetNotFound(targetId.toString()));
            case CombatSystem.AttackOutcome.ForbiddenZone(var zoneName) ->
                connection.send(new CombatForbiddenHere(zoneName));
            case CombatSystem.AttackOutcome.OutOfRange(var targetName) ->
                connection.send(new AttackOutOfRange(targetName));
            case CombatSystem.AttackOutcome.OnCooldown(var remainingMs) ->
                connection.send(new AttackOnCooldown(remainingMs));
        }
    }
}
