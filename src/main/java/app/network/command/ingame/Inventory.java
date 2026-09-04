package app.network.command.ingame;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import app.network.CommandHandler;
import app.domain.actor.instance.CharacterInstance;
import app.domain.item.EquipmentItem;
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

        List<Item> items = character.getInventorySystem().getItems();
        List<app.network.message.ingame.Inventory.Entry> entries = items.stream().map(Inventory::toEntry).toList();

        connection.send(new app.network.message.ingame.Inventory(entries, character.getInventorySystem().getGold()));
    }

    // item.getPAtk()/getArmorCategory()/etc. (Item.java) castent leur template en
    // EquipmentItem sans vérification : appeler ces getters sur une potion/clé
    // lèverait une ClassCastException, d'où ce garde-fou plutôt qu'un appel
    // inconditionnel comme avant ce commit (qui n'exposait aucun de ces champs).
    private static app.network.message.ingame.Inventory.Entry toEntry(Item item) {
        boolean equipment = item.getTemplate() instanceof EquipmentItem;
        return new app.network.message.ingame.Inventory.Entry(item.getId(), item.getName(), item.getGrade(),
                item.getSlot(), item.getType(), item.getDescription(), item.getWeight(),
                equipment ? item.getArmorCategory() : null, equipment ? item.getPAtk() : 0,
                equipment ? item.getMAtk() : 0, equipment ? item.getPDef() : 0, equipment ? item.getMDef() : 0,
                equipment ? item.getAccuracyBonus() : 0, equipment ? item.getEvasionBonus() : 0,
                equipment ? item.getCritBonus() : 0, equipment ? item.getAtkSpd() : 0, item.getEnchant(),
                item.getQuantity());
    }
}
