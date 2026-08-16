package fr.idev.mudserver.domain.actor.system;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import fr.idev.mudserver.config.GameConfig;
import fr.idev.mudserver.domain.actor.component.RestComponent;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.actor.event.LongRestTaken;
import fr.idev.mudserver.domain.actor.event.ShortRestTaken;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.instance.RestOutcome;
import fr.idev.mudserver.domain.item.FoodItem;
import fr.idev.mudserver.domain.item.Item;

public final class RestSystem {

    private RestSystem() {
    }

    public static boolean canTakeShortRest(CharacterInstance character) {
        return component(character).shortRestCount() < CharacterInstance.MAX_SHORT_RESTS_BEFORE_LONG_REST;
    }

    public static void incrementShortRestCount(CharacterInstance character) {
        character.updateComponent(RestComponent.class, current -> new RestComponent(current.shortRestCount() + 1));
    }

    public static void resetShortRestCount(CharacterInstance character) {
        character.updateComponent(RestComponent.class, current -> new RestComponent(0));
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
            int amount = LevelingSystem.hitDieRecovery(character);
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

    private static RestComponent component(CharacterInstance character) {
        return character.component(RestComponent.class);
    }
}
