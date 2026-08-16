package fr.idev.mudserver.controller.ingame;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.actor.system.InventorySystem;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.item.Item;
import fr.idev.mudserver.domain.item.Rarity;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.ItemNotCarried;
import fr.idev.mudserver.network.message.ingame.ItemNotEquipped;
import fr.idev.mudserver.network.message.ingame.ItemUnequipped;

@Component
public class Unequip implements ControllerHandler {

    @Override
    public String name() {
        return "unequip";
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
            connection.send(new Usage("unequip <name>"));
            return;
        }

        Optional<Item> item = character.findOneByName(name);

        if (item.isEmpty()) {
            connection.send(new ItemNotCarried(name));
            return;
        }

        String templateName = item.get().getName();

        if (item.get().getSlot() == null) {
            connection.send(new ItemNotEquipped(templateName));
            return;
        }

        Rarity templateRarity = item.get().getRarity();
        InventorySystem.unequip(character, item.get());

        connection.send(new ItemUnequipped(templateName, templateRarity));
    }
}
