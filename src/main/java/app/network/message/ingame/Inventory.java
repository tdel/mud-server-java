package app.network.message.ingame;

import java.util.List;
import java.util.UUID;

import app.network.OutputJsonMessage;
import app.domain.item.ArmorCategory;
import app.domain.item.EquipmentSlot;
import app.domain.item.ItemGrade;
import app.domain.item.ItemType;

public record Inventory(List<Entry> items, int gold) implements OutputJsonMessage {

    // Champs de combat (pAtk..atkSpd) et armorCategory valent 0/null pour un objet
    // non équipable (potion, clé...) — voir Inventory (command), qui ne les
    // renseigne que pour un Item adossé à un EquipmentItem (cf. Item.equipment(),
    // qui lèverait une ClassCastException sur un template non-équipement).
    public record Entry(UUID id, String name, ItemGrade grade, EquipmentSlot slot, ItemType type, String description,
            int weight, ArmorCategory armorCategory, int pAtk, int mAtk, int pDef, int mDef, int accuracyBonus,
            int evasionBonus, int critBonus, int atkSpd, int enchant) {
    }

}
