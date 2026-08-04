package fr.idev.mudserver.domain;

public enum EquipmentSlot {
    WEAPON, HEAD, CHEST, LEGS, FEET, HANDS, OFF_HAND;

    public String label() {
        return name().toLowerCase();
    }
}
