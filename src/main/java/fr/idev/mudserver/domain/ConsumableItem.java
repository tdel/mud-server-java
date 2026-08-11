package fr.idev.mudserver.domain;

import java.util.UUID;

import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.actor.event.GamePlayerUsedPotion;
import fr.idev.mudserver.game.dice.DiceRoller;

public class ConsumableItem extends ItemTemplate {

    private final ConsumableEffect effect;
    private final String effectDice;

    public ConsumableItem(UUID id, String name, String description, ItemType type, int weight,
            ArmorCategory armorCategory, int baseAc, String damageDice, WeaponCategory weaponCategory, int price,
            Rarity rarity, int bonus, ConsumableEffect effect, String effectDice) {
        super(id, name, description, type, weight, armorCategory, baseAc, damageDice, weaponCategory, price, rarity,
                bonus);
        this.effect = effect;
        this.effectDice = effectDice;
    }

    public void consume(GamePlayer character, Item item) {
        switch (effect) {
            case HEALING -> heal(character, item);
        }
    }

    private void heal(GamePlayer character, Item item) {
        int amount = DiceRoller.roll(effectDice).total();
        int healed = character.heal(amount);
        character.getInventory().removeItem(item);
        DomainEventPublisher.publish(new GamePlayerUsedPotion(character, item, healed));
    }
}
