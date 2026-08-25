package app.network.command.ingame;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import app.network.CommandHandler;
import app.domain.actor.instance.CharacterInstance;
import app.domain.item.Item;
import app.domain.item.Rarity;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.Usage;
import app.network.message.ingame.ItemDiscarded;
import app.network.message.ingame.ItemNotCarried;

@Component
public class Drop implements CommandHandler {

    @Override
    public String name() {
        return "drop";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();
        String name = argument.trim();

        if (name.isEmpty()) {
            connection.send(new Usage("drop <name>"));
            return;
        }

        Optional<Item> item = character.getInventory().findOneByName(name);

        if (item.isEmpty()) {
            connection.send(new ItemNotCarried(name));
            return;
        }

        String templateName = item.get().getName();
        Rarity templateRarity = item.get().getRarity();
        character.discardItem(item.get());

        connection.send(new ItemDiscarded(templateName, templateRarity));
    }
}
