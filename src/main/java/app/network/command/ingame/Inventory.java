package app.network.command.ingame;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import app.network.CommandHandler;
import app.domain.actor.instance.CharacterInstance;
import app.domain.item.Item;
import app.network.Connection;
import app.network.ConnectionState;

@Component
public class Inventory implements CommandHandler {

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
        List<app.network.message.ingame.Inventory.Entry> entries = items.stream()
                .map(item -> new app.network.message.ingame.Inventory.Entry(item.getName(), item.getRarity(),
                        item.getSlot()))
                .toList();

        connection.send(new app.network.message.ingame.Inventory(entries, character.getInventory().getGold()));
    }
}
