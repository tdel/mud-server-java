package fr.idev.mudserver.controller.ingame;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.domain.RoomExit;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.NoSuchExit;

@Component
public class Go implements ControllerHandler {

    private final GameWorld gameWorld;
    private final Look lookAction;

    public Go(GameWorld gameWorld, Look lookAction) {
        this.gameWorld = gameWorld;
        this.lookAction = lookAction;
    }

    @Override
    public String name() {
        return "go";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        Character character = gameWorld.character(connection);
        String direction = argument.trim();

        if (direction.isEmpty()) {
            connection.send(new Usage("go <direction>"));
            return;
        }

        Optional<RoomExit> exit = character.getCurrentRoom().findOneByDirection(direction);
        if (exit.isEmpty()) {
            connection.send(new NoSuchExit(direction));
            return;
        }

        character.moveToRoom(exit.get().getTargetRoom());

        lookAction.onReceive(connection, "");
    }
}
