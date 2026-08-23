package fr.idev.mudserver.domain.item;

import java.util.List;
import java.util.UUID;

import fr.idev.mudserver.domain.*;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.actor.event.GamePlayerUsedManaPotion;
import fr.idev.mudserver.domain.actor.event.GamePlayerUsedPotion;
import fr.idev.mudserver.game.dice.DiceRoller;

public class ConsumableItem extends ItemTemplate {

    private final ConsumableEffect effect;
    private final String effectDice;

    public ConsumableItem(UUID id, String name, String description, ItemType type, int weight,
            ArmorCategory armorCategory, int baseAc, String damageDice, WeaponCategory weaponCategory, int price,
            Rarity rarity, int bonus, List<Spell> grantedSpells, ConsumableEffect effect, String effectDice) {
        super(id, name, description, type, weight, armorCategory, baseAc, damageDice, weaponCategory, price, rarity,
                bonus, grantedSpells);
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
