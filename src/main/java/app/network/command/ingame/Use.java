package app.network.command.ingame;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import app.network.CommandArguments;
import app.network.CommandHandler;
import app.domain.actor.instance.CharacterInstance;
import app.domain.item.ConsumableItem;
import app.domain.item.Item;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.Usage;
import app.network.message.ingame.ItemNotCarried;
import app.network.message.ingame.ItemNotUsable;

@Component
public class Use implements CommandHandler {

    @Override
    public String name() {
        return "use";
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
            connection.send(new Usage("use <uuid>"));
            return;
        }

        Optional<Item> item = CommandArguments.tryParseUuid(raw).flatMap(character.getInventory()::findOneById);
        if (item.isEmpty()) {
            connection.send(new ItemNotCarried(raw));
            return;
        }

        Item resolved = item.get();
        if (resolved.getTemplate() instanceof ConsumableItem consumable) {
            consumable.consume(character, resolved);
        } else {
            connection.send(new ItemNotUsable(resolved.getName()));
        }
    }
}
