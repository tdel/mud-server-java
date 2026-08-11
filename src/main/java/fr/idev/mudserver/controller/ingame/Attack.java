package fr.idev.mudserver.controller.ingame;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.actor.GameMonster;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.game.CombatEngine;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.ingame.NoTargetSelected;
import fr.idev.mudserver.network.message.ingame.TargetNotFound;

@Component
public class Attack implements ControllerHandler {

    private final CombatEngine combatEngine;

    public Attack(CombatEngine combatEngine) {
        this.combatEngine = combatEngine;
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
    public void onReceive(Connection connection, String argument) {
        GamePlayer character = connection.character();
        String name = argument.trim();

        GameMonster target;
        if (name.isEmpty()) {
            target = character.getTarget();
            if (target == null) {
                connection.send(new NoTargetSelected());
                return;
            }
            if (!character.getCurrentRoom().getMonsters().contains(target)) {
                character.setTarget(null);
                connection.send(new TargetNotFound(target.getName()));
                return;
            }
        } else {
            Optional<GameMonster> found = character.getCurrentRoom().findMonsterByName(name);
            if (found.isEmpty()) {
                connection.send(new TargetNotFound(name));
                return;
            }
            target = found.get();
            character.setTarget(target);
        }

        combatEngine.attack(character, target);
    }
}
