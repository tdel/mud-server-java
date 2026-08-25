package app.domain.actor;

public enum ModifiedStat {
    ATTACK_ROLL, ARMOR_CLASS;

    public String label() {
        return switch (this) {
            case ATTACK_ROLL -> "attack rolls";
            case ARMOR_CLASS -> "AC";
        };
    }
}
