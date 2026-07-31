package fr.idev.mudserver.domain;

import java.util.Optional;

public enum ItemType {
    WEAPON,
    HELMET,
    ARMOR,
    PANTS,
    BOOTS,
    GLOVES,
    POTION,
    KEY,
    TOOL,
    MISC;

    public Optional<EquipmentSlot> equipmentSlot() {
        return switch (this) {
            case WEAPON -> Optional.of(EquipmentSlot.WEAPON);
            case HELMET -> Optional.of(EquipmentSlot.HEAD);
            case ARMOR -> Optional.of(EquipmentSlot.CHEST);
            case PANTS -> Optional.of(EquipmentSlot.LEGS);
            case BOOTS -> Optional.of(EquipmentSlot.FEET);
            case GLOVES -> Optional.of(EquipmentSlot.HANDS);
            default -> Optional.empty();
        };
    }
}
