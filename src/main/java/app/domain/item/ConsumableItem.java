package app.domain.item;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.domain.ActiveSkill;
import app.domain.ConsumableEffect;
import app.domain.actor.instance.PlayerInstance;
import app.domain.actor.system.InventorySystem;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.GamePlayerUsedManaPotion;
import app.domain.actor.event.GamePlayerUsedPotion;
import app.domain.actor.event.GamePlayerUsedScroll;
import app.network.message.ingame.CharacterUsedItem;
import app.network.message.ingame.HealthAlreadyFull;
import app.network.message.ingame.ItemOnCooldown;
import app.network.message.ingame.ItemUsed;
import app.network.message.ingame.ManaAlreadyFull;
import app.network.message.ingame.ManaPotionUsed;
import app.network.message.ingame.ScrollUsed;

public class ConsumableItem extends ItemTemplate {

    private static final Logger log = LoggerFactory.getLogger(ConsumableItem.class);

    private final ConsumableEffect effect;
    private final int effectAmount;
    // CAST_SKILL uniquement : sort lancé à l'utilisation (Teleport du Scroll of
    // Escape), null sinon.
    private final ActiveSkill skill;
    // Délai de réutilisation (par template, cf. InventorySystem.isItemReady), 0 =
    // aucun.
    private final int reuseDelayMs;

    public ConsumableItem(UUID id, String name, String description, ItemType type, int weight, int price,
            ItemGrade grade, ConsumableEffect effect, int effectAmount, ActiveSkill skill, int reuseDelayMs) {
        super(id, name, description, type, weight, price, grade);
        this.effect = effect;
        this.effectAmount = effectAmount;
        this.skill = skill;
        this.reuseDelayMs = reuseDelayMs;
    }

    public ActiveSkill getSkill() {
        return skill;
    }

    public int getReuseDelayMs() {
        return reuseDelayMs;
    }

    public void consume(PlayerInstance character, Item item) {
        switch (effect) {
            case HEALING -> heal(character, item);
            case MANA_RESTORE -> restoreMana(character, item);
            case CAST_SKILL -> castSkill(character, item);
        }
    }

    // Parchemin : consommé dès la lecture, puis son sort démarre une incantation
    // normale (SkillCastEngine) sur le lecteur lui-même — un cast interrompu ne
    // rend pas le parchemin. Le délai de réutilisation court dès la lecture.
    private void castSkill(PlayerInstance character, Item item) {
        InventorySystem inventory = character.getInventorySystem();
        if (!inventory.isItemReady(getId())) {
            long remainingMs = inventory.remainingItemCooldown(getId()).toMillis();
            log.debug("item.use.rejected character={} reason=on_cooldown item={} remainingMs={}", character.getId(),
                    item.getId(), remainingMs);
            character.send(new ItemOnCooldown(item.getName(), remainingMs, true));
            return;
        }
        int remaining = inventory.consumeOne(item);
        inventory.markItemCooldown(getId(), reuseDelayMs);
        log.info("item.scroll_used character={} item={} skill={} remaining={}", character.getId(), item.getId(),
                skill.name(), remaining);
        character.send(new ScrollUsed(item.getId(), item.getName(), item.getGrade(), skill.name(), remaining));
        if (reuseDelayMs > 0) {
            character.send(new ItemOnCooldown(item.getName(), reuseDelayMs, false));
        }
        character.broadcast(new CharacterUsedItem(character.getId(), character.getName(), item.getId(), item.getName()),
                character);
        DomainEventPublisher.publish(new GamePlayerUsedScroll(character, item, remaining));
        character.getSkillSystem().castFromItem(skill);
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
