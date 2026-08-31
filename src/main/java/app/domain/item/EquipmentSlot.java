package app.domain.item;

public enum EquipmentSlot {
    WEAPON, HEAD, CHEST, LEGS, FEET, HANDS, OFF_HAND, NECKLACE, LEFT_EARRING, RIGHT_EARRING, LEFT_RING, RIGHT_RING;

    public String label() {
        return name().toLowerCase();
    }
}
