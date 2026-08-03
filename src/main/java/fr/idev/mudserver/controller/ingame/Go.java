package fr.idev.mudserver.controller.ingame;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.RoomExit;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.game.Client;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.NoSuchExit;
import fr.idev.mudserver.persistence.RoomDao;
import fr.idev.mudserver.persistence.RoomExitDao;

@Component
public class Go implements ControllerHandler {

    private final RoomDao roomDao;
    private final RoomExitDao roomExitDao;
    private final GameWorld gameWorld;
    private final Look lookAction;

    public Go(RoomDao roomDao, RoomExitDao roomExitDao, GameWorld gameWorld, Look lookAction) {
        this.roomDao = roomDao;
        this.roomExitDao = roomExitDao;
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
        Client client = gameWorld.client(connection);
        String direction = argument.trim();

        if (direction.isEmpty()) {
            client.send(new Usage("go <direction>"));
            return;
        }

        Optional<RoomExit> exit = roomExitDao.findBySourceRoomIdAndDirection(client.character().getCurrentRoomId(),
                direction);
        if (exit.isEmpty()) {
            client.send(new NoSuchExit(direction));
            return;
        }

        gameWorld.moveClient(client, exit.get().getTargetRoomId());

        lookAction.onReceive(connection, "");
    }
}
