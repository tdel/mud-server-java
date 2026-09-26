package app.network.message.ingame;

import java.util.List;
import java.util.UUID;

import app.network.OutputJsonMessage;
import app.domain.item.ArmorCategory;
import app.domain.item.EquipmentSlot;
import app.domain.item.ItemGrade;
import app.domain.item.ItemType;
import app.domain.item.WeaponType;

// offHandBlocked : une arme à deux mains est équipée, la main secondaire est
// condamnée (cf. InventorySystem.isOffHandBlocked) — le client grise ce slot.
public record Inventory(List<Entry> items, int gold, boolean offHandBlocked) implements OutputJsonMessage {

    // Champs de combat (pAtk..atkSpd) et armorCategory valent 0/null pour un objet
    // non équipable (potion, clé...) — voir Inventory (command), qui ne les
    // renseigne que pour un Item adossé à un EquipmentItem (cf. Item.equipment(),
    // qui lèverait une ClassCastException sur un template non-équipement).
    // weaponType : famille d'arme (null hors arme), voir WeaponType.
    public record Entry(UUID id, String name, ItemGrade grade, EquipmentSlot slot, ItemType type, String description,
            int weight, ArmorCategory armorCategory, int pAtk, int mAtk, int pDef, int mDef, int accuracyBonus,
            int evasionBonus, int critBonus, int atkSpd, int enchant, int quantity, WeaponType weaponType) {
    }

}
