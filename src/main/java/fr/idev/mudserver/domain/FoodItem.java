package fr.idev.mudserver.domain;

import java.util.UUID;

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
