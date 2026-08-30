package app.domain.item;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import app.domain.*;
import app.domain.actor.instance.CharacterInstance;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.GamePlayerUsedManaPotion;
import app.domain.actor.event.GamePlayerUsedPotion;
import app.game.dice.DiceRoller;

public class ConsumableItem extends ItemTemplate {

    private final ConsumableEffect effect;
    private final String effectDice;

    public ConsumableItem(UUID id, String name, String description, ItemType type, int weight,
            ArmorCategory armorCategory, int pAtk, int mAtk, int pDef, int mDef, int accuracyBonus, int evasionBonus,
            int critBonus, int price, Rarity rarity, List<Spell> grantedSpells,
            Map<SpellElement, Integer> elementalResistances, ItemGrade grade, String setId, ConsumableEffect effect,
            String effectDice) {
        super(id, name, description, type, weight, armorCategory, pAtk, mAtk, pDef, mDef, accuracyBonus, evasionBonus,
                critBonus, price, rarity, grantedSpells, elementalResistances, grade, setId);
        this.effect = effect;
        this.effectDice = effectDice;
    }

    public void consume(CharacterInstance character, Item item) {
        switch (effect) {
            case HEALING -> heal(character, item);
            case MANA_RESTORE -> restoreMana(character, item);
        }
    }

    private void heal(CharacterInstance character, Item item) {
        int amount = DiceRoller.roll(effectDice).total();
        int healed = character.heal(amount);
        character.getInventory().removeItem(item);
        DomainEventPublisher.publish(new GamePlayerUsedPotion(character, item, healed));
    }

    private void restoreMana(CharacterInstance character, Item item) {
        int amount = DiceRoller.roll(effectDice).total();
        int restored = character.gainMana(amount);
        character.getInventory().removeItem(item);
        DomainEventPublisher.publish(new GamePlayerUsedManaPotion(character, item, restored));
    }
}
