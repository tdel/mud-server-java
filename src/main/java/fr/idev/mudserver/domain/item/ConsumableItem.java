package fr.idev.mudserver.domain.item;

import java.util.UUID;

import fr.idev.mudserver.domain.*;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.actor.event.GamePlayerUsedPotion;
import fr.idev.mudserver.domain.actor.system.CombatSystem;
import fr.idev.mudserver.domain.actor.system.InventorySystem;
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

    public void consume(CharacterInstance character, Item item, CombatSystem combatSystem,
            InventorySystem inventorySystem) {
        switch (effect) {
            case HEALING -> heal(character, item, combatSystem, inventorySystem);
        }
    }

    private void heal(CharacterInstance character, Item item, CombatSystem combatSystem,
            InventorySystem inventorySystem) {
        int amount = DiceRoller.roll(effectDice).total();
        int healed = combatSystem.heal(character, amount);
        inventorySystem.removeItem(character, item);
        DomainEventPublisher.publish(new GamePlayerUsedPotion(character, item, healed));
    }
}
