package fr.idev.mudserver.domain;

import java.util.UUID;

/**
 * {@link ItemTemplate} de type {@link ItemType#FOOD} — une provision (pomme,
 * pain, oeufs...) dont {@code nutritionValue} contribue au seuil de repos long
 * (voir {@code game.actor.RestService}), distinct de {@code price} (coût en or
 * chez le marchand). Contrairement à {@link ConsumableItem}, une nourriture ne
 * se consomme jamais via {@code use <item>} : elle n'est sélectionnée et
 * détruite que par le flux de repos long ({@code controller.ingame.Rest}).
 */
public class FoodItem extends ItemTemplate {

    private final int nutritionValue;

    public FoodItem(UUID id, String name, String description, ItemType type, int weight, ArmorCategory armorCategory,
            int baseAc, String damageDice, WeaponCategory weaponCategory, int price, Rarity rarity, int bonus,
            int nutritionValue) {
        super(id, name, description, type, weight, armorCategory, baseAc, damageDice, weaponCategory, price, rarity,
                bonus);
        this.nutritionValue = nutritionValue;
    }

    public int getNutritionValue() {
        return nutritionValue;
    }
}
