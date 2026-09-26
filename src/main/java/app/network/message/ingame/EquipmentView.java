package app.network.message.ingame;

import java.util.List;

import app.domain.actor.instance.PlayerInstance;
import app.domain.item.ArmorCategory;
import app.domain.item.EquipmentItem;
import app.domain.item.EquipmentSlot;
import app.domain.item.Item;
import app.domain.item.ItemGrade;
import app.domain.item.ItemType;
import app.domain.item.WeaponType;

/**
 * Pièce d'équipement portée, telle que vue par les AUTRES joueurs — juste de
 * quoi l'afficher sur le personnage côté client (modèle d'arme/armure), sans
 * les stats de combat (réservées à l'Inventory du porteur).
 */
public record EquipmentView(EquipmentSlot slot, String name, ItemType type, ItemGrade grade,
        ArmorCategory armorCategory, WeaponType weaponType) {

    public static EquipmentView of(Item item) {
        boolean equipment = item.getTemplate() instanceof EquipmentItem;
        return new EquipmentView(item.getSlot(), item.getName(), item.getType(), item.getGrade(),
                equipment ? item.getArmorCategory() : null, equipment ? item.getWeaponType() : null);
    }

    public static List<EquipmentView> listOf(PlayerInstance player) {
        return player.getInventorySystem().getEquippedItems().stream().map(EquipmentView::of).toList();
    }
}
