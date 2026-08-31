package app.domain.item;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import app.domain.Spell;
import app.domain.SpellElement;

public class FoodItem extends ItemTemplate {

    private final int nutritionValue;

    public FoodItem(UUID id, String name, String description, ItemType type, int weight, ArmorCategory armorCategory,
            int pAtk, int mAtk, int pDef, int mDef, int accuracyBonus, int evasionBonus, int critBonus, int atkSpd,
            int price, List<Spell> grantedSpells, Map<SpellElement, Integer> elementalResistances, ItemGrade grade,
            String setId, int nutritionValue) {
        super(id, name, description, type, weight, armorCategory, pAtk, mAtk, pDef, mDef, accuracyBonus, evasionBonus,
                critBonus, atkSpd, price, grantedSpells, elementalResistances, grade, setId);
        this.nutritionValue = nutritionValue;
    }

    public int getNutritionValue() {
        return nutritionValue;
    }
}
