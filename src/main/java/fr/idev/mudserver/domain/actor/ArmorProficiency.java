package fr.idev.mudserver.domain.actor;

import fr.idev.mudserver.domain.ArmorCategory;

/**
 * Distinct de {@link ArmorCategory} : une maîtrise d'armure DnD5e couvre aussi
 * les boucliers, qui n'ont pas de {@link ArmorCategory} (voir
 * {@code data/items.json}, où un bouclier n'a jamais de champ
 * {@code armorCategory}) — {@link #SHIELDS} se dérive donc du
 * {@code ItemType.SHIELD} de l'item, jamais de {@link #of(ArmorCategory)}.
 */
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
