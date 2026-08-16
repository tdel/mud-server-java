package fr.idev.mudserver.domain.actor.system;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import fr.idev.mudserver.config.GameConfig;
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.event.CharacterGainedXp;
import fr.idev.mudserver.domain.actor.event.CharacterLeveledUp;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.actor.event.LongRestTaken;
import fr.idev.mudserver.domain.actor.event.ShortRestTaken;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.instance.RestOutcome;
import fr.idev.mudserver.domain.actor.component.LevelingComponent;
import fr.idev.mudserver.domain.item.FoodItem;
import fr.idev.mudserver.domain.item.Item;

public final class LevelingSystem {

    private LevelingSystem() {
    }

    public static void attach(CharacterInstance character, int level, int xp, int shortRestCount) {
        character.attachComponent(new LevelingComponent(level, xp, shortRestCount));
    }

    public static int level(CharacterInstance character) {
        return component(character).level();
    }

    public static int xp(CharacterInstance character) {
        return component(character).xp();
    }

    public static int shortRestCount(CharacterInstance character) {
        return component(character).shortRestCount();
    }

    public static void gainXp(CharacterInstance character, int amount) {
        character.updateComponent(LevelingComponent.class,
                current -> new LevelingComponent(current.level(), current.xp() + amount, current.shortRestCount()));
        DomainEventPublisher.publish(new CharacterGainedXp(character, amount));
    }

    public static int hitDieRecovery(CharacterInstance character) {
        int hitDie = character.getCharacterClass().hitDie();
        return Math.max(1, hitDie / 2 + 1 + character.getModifier(Attribute.CONSTITUTION));
    }

    public static void applyLevelUp(CharacterInstance character) {
        int hpGain = hitDieRecovery(character);
        int newLevel = character
                .updateComponent(LevelingComponent.class,
                        current -> new LevelingComponent(current.level() + 1, current.xp(), current.shortRestCount()))
                .level();
        CombatSystem.increaseMaxHealth(character, hpGain);
        DomainEventPublisher.publish(new CharacterLeveledUp(character, newLevel, hpGain));
    }

    public static boolean canTakeShortRest(CharacterInstance character) {
        return shortRestCount(character) < CharacterInstance.MAX_SHORT_RESTS_BEFORE_LONG_REST;
    }

    public static void incrementShortRestCount(CharacterInstance character) {
        character.updateComponent(LevelingComponent.class,
                current -> new LevelingComponent(current.level(), current.xp(), current.shortRestCount() + 1));
    }

    public static void resetShortRestCount(CharacterInstance character) {
        character.updateComponent(LevelingComponent.class,
                current -> new LevelingComponent(current.level(), current.xp(), 0));
    }

    public static RestOutcome doShortRest(CharacterInstance initiator) {
        if (initiator.isInCombat()) {
            return new RestOutcome.InCombat();
        }
        if (!canTakeShortRest(initiator)) {
            return new RestOutcome.NoShortRestLeft();
        }

        Map<CharacterInstance, Integer> healedAmounts = new LinkedHashMap<>();
        for (CharacterInstance character : initiator.getWorldInstance().onlineCharacters()) {
            int amount = hitDieRecovery(character);
            healedAmounts.put(character, CombatSystem.heal(character, amount));
            incrementShortRestCount(character);
        }

        DomainEventPublisher.publish(new ShortRestTaken(initiator, healedAmounts));
        return new RestOutcome.Rested(healedAmounts);
    }

    public static RestOutcome doLongRest(CharacterInstance initiator, List<Item> selectedFood) {
        if (initiator.isInCombat()) {
            return new RestOutcome.InCombat();
        }

        int totalValue = selectedFood.stream().mapToInt(item -> ((FoodItem) item.getTemplate()).getNutritionValue())
                .sum();
        if (totalValue < GameConfig.LONG_REST_PROVISION_THRESHOLD) {
            return new RestOutcome.NotEnoughProvisions(totalValue);
        }

        for (Item food : selectedFood) {
            InventorySystem.removeItem(initiator, food);
        }

        Map<CharacterInstance, Integer> healedAmounts = new LinkedHashMap<>();
        for (CharacterInstance character : initiator.getWorldInstance().onlineCharacters()) {
            healedAmounts.put(character, CombatSystem.heal(character, character.getMaxHealth()));
            resetShortRestCount(character);
        }

        DomainEventPublisher.publish(new LongRestTaken(initiator, healedAmounts, selectedFood));
        return new RestOutcome.Rested(healedAmounts);
    }

    private static LevelingComponent component(CharacterInstance character) {
        return character.component(LevelingComponent.class);
    }
}
