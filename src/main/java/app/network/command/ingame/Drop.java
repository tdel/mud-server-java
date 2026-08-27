package app.network.command.ingame;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import app.network.CommandArguments;
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
        String raw = argument.trim();

        if (raw.isEmpty()) {
            connection.send(new Usage("drop <uuid>"));
            return;
        }

        Optional<Item> item = CommandArguments.tryParseUuid(raw).flatMap(character.getInventory()::findOneById);

        if (item.isEmpty()) {
            connection.send(new ItemNotCarried(raw));
            return;
        }

        UUID templateId = item.get().getId();
        String templateName = item.get().getName();
        Rarity templateRarity = item.get().getRarity();
        character.discardItem(item.get());

        connection.send(new ItemDiscarded(templateId, templateName, templateRarity));
    }
}
