package app.domain.actor;

import app.domain.item.ArmorCategory;

public enum ArmorProficiency {
    LIGHT, MEDIUM, HEAVY, SHIELDS;

    public static ArmorProficiency of(ArmorCategory category) {
        return switch (category) {
            case LIGHT -> LIGHT;
            case MEDIUM -> MEDIUM;
            case HEAVY -> HEAVY;
        };
    }
}
