package fr.idev.mudserver.controller.ingame;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.GameMonster;
import fr.idev.mudserver.domain.GamePlayer;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.TargetNotFound;
import fr.idev.mudserver.network.message.ingame.TargetSelected;

@Component
public class Select implements ControllerHandler {

    private final GameWorld gameWorld;

    public Select(GameWorld gameWorld) {
        this.gameWorld = gameWorld;
    }

    @Override
    public String name() {
        return "select";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        GamePlayer character = gameWorld.character(connection);
        String name = argument.trim();

        if (name.isEmpty()) {
            connection.send(new Usage("select <monster name>"));
            return;
        }

        Optional<GameMonster> target = character.getCurrentRoom().findMonsterByName(name);
        if (target.isEmpty()) {
            connection.send(new TargetNotFound(name));
            return;
        }

        character.setTarget(target.get());
        connection.send(new TargetSelected(target.get().getName()));
    }
}
