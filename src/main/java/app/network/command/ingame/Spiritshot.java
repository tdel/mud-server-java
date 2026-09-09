package app.network.command.ingame;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.ShotGradeToggled;
import app.domain.actor.instance.PlayerInstance;
import app.domain.item.ItemGrade;
import app.domain.item.ItemType;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.CommandHandler;
import app.network.message.Usage;
import app.network.message.ingame.InvalidShotGrade;
import app.network.message.ingame.ShotGradeChanged;

@Component
public class Spiritshot implements CommandHandler {

    @Override
    public String name() {
        return "spiritshot";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        PlayerInstance character = connection.character();
        String raw = argument.trim();

        if (raw.isEmpty()) {
            connection.send(new Usage("spiritshot <nograde|d|c|b|a|s|off>"));
            return;
        }

        if (ShotGradeArgument.OFF.equalsIgnoreCase(raw)) {
            character.getInventorySystem().setActiveSpiritshotGrade(null);
            character.send(new ShotGradeChanged(ItemType.SPIRITSHOT, null));
            DomainEventPublisher.publish(new ShotGradeToggled(character, ItemType.SPIRITSHOT, null));
            return;
        }

        Optional<ItemGrade> requested = ShotGradeArgument.parse(raw);
        if (requested.isEmpty()) {
            connection.send(new InvalidShotGrade(raw));
            return;
        }

        ItemGrade newGrade = requested.get() == character.getInventorySystem().getActiveSpiritshotGrade()
                ? null
                : requested.get();
        character.getInventorySystem().setActiveSpiritshotGrade(newGrade);
        character.send(new ShotGradeChanged(ItemType.SPIRITSHOT, newGrade));
        DomainEventPublisher.publish(new ShotGradeToggled(character, ItemType.SPIRITSHOT, newGrade));
    }
}
