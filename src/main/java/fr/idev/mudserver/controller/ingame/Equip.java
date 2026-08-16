package fr.idev.mudserver.controller.ingame;

import java.util.Optional;
import java.util.Set;

import fr.idev.mudserver.domain.actor.component.InventoryComponent;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.actor.system.InventorySystem;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.item.EquipmentSlot;
import fr.idev.mudserver.domain.item.Item;
import fr.idev.mudserver.domain.item.Rarity;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.ItemEquipped;
import fr.idev.mudserver.network.message.ingame.ItemNotCarried;
import fr.idev.mudserver.network.message.ingame.ItemNotEquippable;

@Component
public class Equip implements ControllerHandler {

    @Override
    public String name() {
        return "equip";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        String name = argument.trim();
        if (name.isEmpty()) {
            connection.send(new Usage("equip <name>"));
            return;
        }

        CharacterInstance character = connection.character();

        Optional<Item> itemQuery = character.component(InventoryComponent.class).findOneByName(name);
        if (itemQuery.isEmpty()) {
            connection.send(new ItemNotCarried(name));
            return;
        }
        Item item = itemQuery.get();

        Optional<EquipmentSlot> slotQuery = InventorySystem.equip(character, item);
        if (slotQuery.isEmpty()) {
            connection.send(new ItemNotEquippable(item));
            return;
        }

        connection.send(new ItemEquipped(item, slotQuery.get()));
    }
}
