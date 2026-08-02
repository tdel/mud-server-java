package fr.idev.mudserver.controller.ingame;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.domain.RoomExit;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.game.PlayerInstance;
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
    public void onReceive(Connection session, String argument) {
        PlayerInstance player = session.player();
        String direction = argument.trim();

        if (direction.isEmpty()) {
            player.send(new Usage("go <direction>"));
            return;
        }

        UUID oldRoomId = player.currentRoomId();

        Optional<RoomExit> exit = roomExitDao.findBySourceRoomIdAndDirection(oldRoomId, direction);
        if (exit.isEmpty()) {
            player.send(new NoSuchExit(direction));
            return;
        }

        UUID newRoomId = exit.get().targetRoomId();
        Room newRoom = roomDao.findById(newRoomId).orElseThrow();

        gameWorld.roomInstance(oldRoomId).leave(player, newRoom);
        gameWorld.roomInstance(newRoomId).join(player);

        lookAction.onReceive(session, "");
    }
}
