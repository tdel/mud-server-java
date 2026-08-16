package fr.idev.mudserver.domain.item;

public enum EquipmentSlot {
    WEAPON, HEAD, CHEST, LEGS, FEET, HANDS, OFF_HAND;

    public String label() {
        return name().toLowerCase();
    }
}
