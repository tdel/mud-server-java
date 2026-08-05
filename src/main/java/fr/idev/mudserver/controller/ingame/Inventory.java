package fr.idev.mudserver.controller.ingame;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;

@Component
public class Inventory implements ControllerHandler {

    private final GameWorld gameWorld;

    public Inventory(GameWorld gameWorld) {
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
        GamePlayer character = gameWorld.character(connection);

        List<Item> items = character.getInventory();
        List<String> names = items.stream().map(Item::getName).toList();

        connection.send(new fr.idev.mudserver.network.message.ingame.Inventory(names));
    }
}
