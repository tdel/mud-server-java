package app.domain.item;

import java.util.List;
import java.util.UUID;

import app.domain.Spell;

public class FoodItem extends ItemTemplate {

    private final int nutritionValue;

    public FoodItem(UUID id, String name, String description, ItemType type, int weight, ArmorCategory armorCategory,
            int pAtk, int mAtk, int pDef, int mDef, int accuracyBonus, int evasionBonus, int critBonus, int price,
            Rarity rarity, List<Spell> grantedSpells, int nutritionValue) {
        super(id, name, description, type, weight, armorCategory, pAtk, mAtk, pDef, mDef, accuracyBonus, evasionBonus,
                critBonus, price, rarity, grantedSpells);
        this.nutritionValue = nutritionValue;
    }

    public int getNutritionValue() {
        return nutritionValue;
    }
}
