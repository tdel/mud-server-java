package fr.idev.mudserver.controller.ingame;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.actor.GameMonster;
import fr.idev.mudserver.domain.actor.GameNpc;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.domain.RoomExit;
import fr.idev.mudserver.game.GameWorld;
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

        List<RoomExit> exits = room.getExits();
        List<GamePlayer> characters = room.characters();
        List<Item> items = room.getItems();
        List<GameMonster> monsters = room.getMonsters();
        List<GameNpc> npcs = room.getNpcs();

        List<String> exitNames = exits.stream().map(RoomExit::getDirection).toList();
        List<String> characterNames = characters.stream().filter(other -> !other.getId().equals(character.getId()))
                .map(GamePlayer::getName).toList();
        List<String> itemNames = items.stream().map(Item::getName).toList();
        List<String> monsterNames = monsters.stream().map(GameMonster::getName).toList();
        List<String> npcNames = npcs.stream().map(GameNpc::getName).toList();

        return new RoomDescription(room.getName(), room.getDescription(), exitNames, characterNames, itemNames,
                monsterNames, npcNames);
    }
}
