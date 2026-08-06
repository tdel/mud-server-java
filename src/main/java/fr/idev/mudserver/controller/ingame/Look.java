package fr.idev.mudserver.controller.ingame;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.domain.actor.GameCharacter;
import fr.idev.mudserver.domain.actor.GameMonster;
import fr.idev.mudserver.domain.actor.GameNpc;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.game.HexGridRenderer;
import fr.idev.mudserver.game.RoomService;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.ingame.RoomDescription;

@Component
public class Look implements ControllerHandler {

    private final GameWorld gameWorld;
    private final RoomService roomService;

    public Look(GameWorld gameWorld, RoomService roomService) {
        this.gameWorld = gameWorld;
        this.roomService = roomService;
    }

    @Override
    public String name() {
        return "look";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        GamePlayer character = gameWorld.character(connection);
        connection.send(describeRoom(character));
    }

    private RoomDescription describeRoom(GamePlayer character) {
        Room room = character.getCurrentRoom();

        List<String> gridLines = HexGridRenderer.render(room, character);
        List<GameCharacter> nearby = room.occupantsWithin(character.getPosition(), HexGridRenderer.VIEWPORT_RADIUS);

        List<String> portalSummaries = room.getPortals().stream()
                .map(portal -> portal.direction() + ": " + portal.targetRoom().getName()).toList();
        List<String> characterNames = nearby.stream().filter(GamePlayer.class::isInstance)
                .filter(other -> !other.getId().equals(character.getId())).map(GameCharacter::getName).toList();
        List<String> itemNames = room.getItems().stream().map(Item::getName).toList();
        List<String> monsterNames = nearby.stream().filter(GameMonster.class::isInstance).map(GameCharacter::getName)
                .toList();
        List<String> npcNames = nearby.stream().filter(GameNpc.class::isInstance).map(GameCharacter::getName).toList();

        return new RoomDescription(room.getName(), room.getDescription(), gridLines, HexGridRenderer.LEGEND,
                portalSummaries, characterNames, itemNames, monsterNames, npcNames);
    }
}
