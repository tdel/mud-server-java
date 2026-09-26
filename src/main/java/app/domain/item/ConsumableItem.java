package app.domain.item;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.domain.ConsumableEffect;
import app.domain.actor.instance.PlayerInstance;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.GamePlayerUsedManaPotion;
import app.domain.actor.event.GamePlayerUsedPotion;
import app.network.message.ingame.CharacterUsedItem;
import app.network.message.ingame.HealthAlreadyFull;
import app.network.message.ingame.ItemUsed;
import app.network.message.ingame.ManaAlreadyFull;
import app.network.message.ingame.ManaPotionUsed;

public class ConsumableItem extends ItemTemplate {

    private static final Logger log = LoggerFactory.getLogger(ConsumableItem.class);

    private final ConsumableEffect effect;
    private final int effectAmount;

    public ConsumableItem(UUID id, String name, String description, ItemType type, int weight, int price,
            ItemGrade grade, ConsumableEffect effect, int effectAmount) {
        super(id, name, description, type, weight, price, grade);
        this.effect = effect;
        this.effectAmount = effectAmount;
    }

    public void consume(PlayerInstance character, Item item) {
        switch (effect) {
            case HEALING -> heal(character, item);
            case MANA_RESTORE -> restoreMana(character, item);
        }
    }

    private void heal(PlayerInstance character, Item item) {
        // Déjà au maximum : refus sans consommer la potion.
        int currentHealth = character.getResourceSystem().getCurrentHealth();
        int maxHealth = character.getResourceSystem().getMaxHealth();
        if (currentHealth >= maxHealth) {
            log.debug("item.use.rejected character={} reason=health_full item={}", character.getId(), item.getId());
            character.send(new HealthAlreadyFull(item.getName(), currentHealth, maxHealth));
            return;
        }
        int healed = character.getResourceSystem().heal(effectAmount);
        int remaining = character.getInventorySystem().consumeOne(item);
        character.send(new ItemUsed(item.getId(), item.getName(), item.getGrade(), healed,
                character.getResourceSystem().getCurrentHealth(), character.getResourceSystem().getMaxHealth(),
                remaining));
        character.broadcast(new CharacterUsedItem(character.getId(), character.getName(), item.getId(), item.getName()),
                character);
        DomainEventPublisher.publish(new GamePlayerUsedPotion(character, item, healed, remaining));
    }

    private void restoreMana(PlayerInstance character, Item item) {
        int currentMana = character.getResourceSystem().getCurrentMana();
        int maxMana = character.getResourceSystem().getMaxMana();
        if (currentMana >= maxMana) {
            log.debug("item.use.rejected character={} reason=mana_full item={}", character.getId(), item.getId());
            character.send(new ManaAlreadyFull(item.getName(), currentMana, maxMana));
            return;
        }
        int restored = character.getResourceSystem().gainMana(effectAmount);
        int remaining = character.getInventorySystem().consumeOne(item);
        character.send(new ManaPotionUsed(item.getId(), item.getName(), item.getGrade(), restored,
                character.getResourceSystem().getCurrentMana(), character.getResourceSystem().getMaxMana(), remaining));
        character.broadcast(new CharacterUsedItem(character.getId(), character.getName(), item.getId(), item.getName()),
                character);
        DomainEventPublisher.publish(new GamePlayerUsedManaPotion(character, item, restored, remaining));
    }
}
