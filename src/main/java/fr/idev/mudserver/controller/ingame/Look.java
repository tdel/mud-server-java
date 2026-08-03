package fr.idev.mudserver.controller.ingame;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.ItemTemplate;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.domain.RoomExit;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.ingame.RoomDescription;
import fr.idev.mudserver.persistence.ItemDao;
import fr.idev.mudserver.persistence.ItemTemplateDao;
import fr.idev.mudserver.persistence.RoomDao;
import fr.idev.mudserver.persistence.RoomExitDao;

@Component
public class Look implements ControllerHandler {

    private final RoomDao roomDao;
    private final RoomExitDao roomExitDao;
    private final ItemDao itemDao;
    private final ItemTemplateDao itemTemplateDao;
    private final GameWorld gameWorld;

    public Look(RoomDao roomDao, RoomExitDao roomExitDao, ItemDao itemDao, ItemTemplateDao itemTemplateDao,
            GameWorld gameWorld) {
        this.roomDao = roomDao;
        this.roomExitDao = roomExitDao;
        this.itemDao = itemDao;
        this.itemTemplateDao = itemTemplateDao;
        this.gameWorld = gameWorld;
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
        Character character = gameWorld.character(connection);
        connection.send(describeRoom(character));
    }

    private RoomDescription describeRoom(Character character) {
        UUID roomId = character.getCurrentRoomId();
        Room room = roomDao.findById(roomId).orElseThrow();

        List<RoomExit> exits = roomExitDao.findBySourceRoomId(roomId);
        List<Character> characters = gameWorld.roomInstance(roomId).characters();
        List<Item> items = itemDao.findByRoomId(roomId);

        List<String> exitNames = exits.stream().map(RoomExit::getDirection).toList();
        List<String> characterNames = characters.stream().filter(other -> !other.getId().equals(character.getId()))
                .map(Character::getName).toList();
        List<String> itemNames = items.stream()
                .map(item -> itemTemplateDao.findById(item.getTemplateId()).map(ItemTemplate::getName).orElseThrow())
                .toList();

        return new RoomDescription(room.getName(), room.getDescription(), exitNames, characterNames, itemNames);
    }
}
