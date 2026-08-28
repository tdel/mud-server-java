package app.network.command.ingame;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import app.network.CommandArguments;
import app.network.CommandHandler;
import app.domain.actor.instance.CharacterInstance;
import app.domain.item.EquipmentSlot;
import app.domain.item.Item;
import app.domain.item.Rarity;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.Usage;
import app.network.message.ingame.AlreadyCasting;
import app.network.message.ingame.ItemEquipped;
import app.network.message.ingame.ItemNotCarried;
import app.network.message.ingame.ItemNotEquippable;

@Component
public class Equip implements CommandHandler {

    @Override
    public String name() {
        return "equip";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public boolean requiresAlive() {
        return true;
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();

        if (character.isCasting()) {
            connection.send(new AlreadyCasting());
            return;
        }

        String raw = argument.trim();

        if (raw.isEmpty()) {
            connection.send(new Usage("equip <uuid>"));
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
        Optional<EquipmentSlot> slot = character.equipItem(item.get());

        if (slot.isEmpty()) {
            connection.send(new ItemNotEquippable(templateName));
            return;
        }

        connection.send(new ItemEquipped(templateId, templateName, templateRarity, slot.get()));
    }
}
