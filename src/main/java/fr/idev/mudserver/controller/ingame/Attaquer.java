package fr.idev.mudserver.controller.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.GameMonster;
import fr.idev.mudserver.domain.GamePlayer;
import fr.idev.mudserver.game.CombatResult;
import fr.idev.mudserver.game.CombatService;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.ingame.AttackResult;
import fr.idev.mudserver.network.message.ingame.NoTargetSelected;
import fr.idev.mudserver.network.message.ingame.TargetNotFound;

@Component
public class Attaquer implements ControllerHandler {

    private final GameWorld gameWorld;
    private final CombatService combatService;

    public Attaquer(GameWorld gameWorld, CombatService combatService) {
        this.gameWorld = gameWorld;
        this.combatService = combatService;
    }

    @Override
    public String name() {
        return "attaquer";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        GamePlayer character = gameWorld.character(connection);
        GameMonster target = character.getTarget();

        if (target == null) {
            connection.send(new NoTargetSelected());
            return;
        }

        if (!character.getCurrentRoom().getMonsters().contains(target)) {
            character.setTarget(null);
            connection.send(new TargetNotFound(target.getName()));
            return;
        }

        CombatResult result = combatService.attack(character, target);
        connection.send(new AttackResult(result));
    }
}
