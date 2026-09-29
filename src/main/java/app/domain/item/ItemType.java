package app.domain.item;

import java.util.List;

public enum ItemType {
    WEAPON, HELMET, ARMOR, PANTS, BOOTS, GLOVES, SHIELD, NECKLACE, EARRING, RING, POTION, SCROLL, KEY, TOOL, MISC, SOULSHOT, SPIRITSHOT;

    // Candidate slots in preference order: RING/EARRING have two interchangeable
    // slots, the rest exactly one.
    public List<EquipmentSlot> equipmentSlots() {
        return switch (this) {
            case WEAPON -> List.of(EquipmentSlot.WEAPON);
            case HELMET -> List.of(EquipmentSlot.HEAD);
            case ARMOR -> List.of(EquipmentSlot.CHEST);
            case PANTS -> List.of(EquipmentSlot.LEGS);
            case BOOTS -> List.of(EquipmentSlot.FEET);
            case GLOVES -> List.of(EquipmentSlot.HANDS);
            case SHIELD -> List.of(EquipmentSlot.OFF_HAND);
            case NECKLACE -> List.of(EquipmentSlot.NECKLACE);
            case EARRING -> List.of(EquipmentSlot.LEFT_EARRING, EquipmentSlot.RIGHT_EARRING);
            case RING -> List.of(EquipmentSlot.LEFT_RING, EquipmentSlot.RIGHT_RING);
            default -> List.of();
        };
    }

    // Taille maximale d'une pile (Item.quantity) : 1 = non stackable (un Item par
    // exemplaire). Les soulshots/spiritshots n'ont qu'une pile illimitée par
    // type+grade (cf. InventorySystem.findStackable/consumeShot) ; les potions se
    // répartissent en piles de 100 au plus, comme les parchemins (cf.
    // InventorySystem.store).
    public int maxStack() {
        return switch (this) {
            case SOULSHOT, SPIRITSHOT -> Integer.MAX_VALUE;
            case POTION, SCROLL -> 100;
            default -> 1;
        };
    }

    public boolean stackable() {
        return maxStack() > 1;
    }
}
