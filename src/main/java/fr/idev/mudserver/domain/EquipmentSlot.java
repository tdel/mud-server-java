package fr.idev.mudserver.domain;

public enum EquipmentSlot {
    WEAPON,
    HEAD,
    CHEST,
    LEGS,
    FEET,
    HANDS;

    public String label() {
        return name().toLowerCase();
    }
}
