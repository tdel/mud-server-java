package fr.idev.mudserver.controller.ingame;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.game.ItemService;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;

@Component
public class Inventory implements ControllerHandler {

    private final ItemService itemService;
    private final GameWorld gameWorld;

    public Inventory(ItemService itemService, GameWorld gameWorld) {
        this.itemService = itemService;
        this.gameWorld = gameWorld;
    }

    @Override
    public String name() {
        return "inventory";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        Character character = gameWorld.character(connection);

        List<Item> items = itemService.getInventory(character);
        List<String> names = items.stream().map(Item::getName).toList();

        connection.send(new fr.idev.mudserver.network.message.ingame.Inventory(names));
    }
}
