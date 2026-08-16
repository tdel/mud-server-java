package fr.idev.mudserver.controller.ingame;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.item.Item;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;

@Component
public class Inventory implements ControllerHandler {

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
        CharacterInstance character = connection.character();

        List<Item> items = character.getInventory().getItems();
        List<fr.idev.mudserver.network.message.ingame.Inventory.Entry> entries = items.stream().map(
                item -> new fr.idev.mudserver.network.message.ingame.Inventory.Entry(item.getName(), item.getRarity()))
                .toList();

        connection.send(
                new fr.idev.mudserver.network.message.ingame.Inventory(entries, character.getInventory().getGold()));
    }
}
