package app.domain.actor;

import java.util.List;

public enum Subclass {
    WARRIOR, KNIGHT, ROGUE, WIZARD, CLERIC;

    public String label() {
        return switch (this) {
            case WARRIOR -> "Warrior";
            case KNIGHT -> "Knight";
            case ROGUE -> "Rogue";
            case WIZARD -> "Wizard";
            case CLERIC -> "Cleric";
        };
    }

    // tier 1 = niveau 20, tier 2 = niveau 40 ; liste vide = aucun choix disponible
    // pour ce palier.
    public static List<Subclass> availableAt(CharacterClass baseClass, int tier) {
        if (tier == 1) {
            return switch (baseClass) {
                case FIGHTER -> List.of(WARRIOR, KNIGHT, ROGUE);
                case MYSTIC -> List.of(WIZARD, CLERIC);
            };
        }
        return List.of();
    }
}
