package app.domain.item;

import java.util.UUID;

import app.domain.ConsumableEffect;
import app.domain.actor.instance.CharacterInstance;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.GamePlayerUsedManaPotion;
import app.domain.actor.event.GamePlayerUsedPotion;

public class ConsumableItem extends ItemTemplate {

    private final ConsumableEffect effect;
    private final int effectAmount;

    public ConsumableItem(UUID id, String name, String description, ItemType type, int weight, int price,
            ItemGrade grade, ConsumableEffect effect, int effectAmount) {
        super(id, name, description, type, weight, price, grade);
        this.effect = effect;
        this.effectAmount = effectAmount;
    }

    public void consume(CharacterInstance character, Item item) {
        switch (effect) {
            case HEALING -> heal(character, item);
            case MANA_RESTORE -> restoreMana(character, item);
        }
    }

    private void heal(CharacterInstance character, Item item) {
        int healed = character.heal(effectAmount);
        character.getInventorySystem().removeItem(item);
        DomainEventPublisher.publish(new GamePlayerUsedPotion(character, item, healed));
    }

    private void restoreMana(CharacterInstance character, Item item) {
        int restored = character.gainMana(effectAmount);
        character.getInventorySystem().removeItem(item);
        DomainEventPublisher.publish(new GamePlayerUsedManaPotion(character, item, restored));
    }
}
