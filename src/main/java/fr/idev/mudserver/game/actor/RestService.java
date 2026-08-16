package fr.idev.mudserver.game.actor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.item.FoodItem;
import fr.idev.mudserver.domain.item.Item;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.actor.event.LongRestTaken;
import fr.idev.mudserver.domain.actor.event.ShortRestTaken;

@Service
public class RestService {

    private static final Logger log = LoggerFactory.getLogger(RestService.class);

    public static final int LONG_REST_PROVISION_THRESHOLD = 20;

    public sealed interface RestOutcome {

        record Rested(Map<CharacterInstance, Integer> healedAmounts) implements RestOutcome {
        }

        record InCombat() implements RestOutcome {
        }

        record NoShortRestLeft() implements RestOutcome {
        }

        record NotEnoughProvisions(int totalValue) implements RestOutcome {
        }
    }

    public RestOutcome shortRest(CharacterInstance initiator) {
        if (initiator.isInCombat()) {
            return new RestOutcome.InCombat();
        }
        if (!initiator.canTakeShortRest()) {
            return new RestOutcome.NoShortRestLeft();
        }

        Map<CharacterInstance, Integer> healedAmounts = new LinkedHashMap<>();
        for (CharacterInstance character : initiator.getWorldInstance().onlineCharacters()) {
            int amount = character.hitDieRecovery();
            healedAmounts.put(character, character.heal(amount));
            character.incrementShortRestCount();
        }

        log.info("rest.short_rest_taken initiator={} affected={}", initiator.getName(), healedAmounts.size());
        DomainEventPublisher.publish(new ShortRestTaken(initiator, healedAmounts));
        return new RestOutcome.Rested(healedAmounts);
    }

    public RestOutcome longRest(CharacterInstance initiator, List<Item> selectedFood) {
        if (initiator.isInCombat()) {
            return new RestOutcome.InCombat();
        }

        int totalValue = selectedFood.stream().mapToInt(item -> ((FoodItem) item.getTemplate()).getNutritionValue())
                .sum();
        if (totalValue < LONG_REST_PROVISION_THRESHOLD) {
            return new RestOutcome.NotEnoughProvisions(totalValue);
        }

        for (Item food : selectedFood) {
            initiator.getInventory().removeItem(food);
        }

        Map<CharacterInstance, Integer> healedAmounts = new LinkedHashMap<>();
        for (CharacterInstance character : initiator.getWorldInstance().onlineCharacters()) {
            healedAmounts.put(character, character.heal(character.getMaxHealth()));
            character.resetShortRestCount();
        }

        log.info("rest.long_rest_taken initiator={} affected={} provisionsConsumed={}", initiator.getName(),
                healedAmounts.size(), selectedFood.size());
        DomainEventPublisher.publish(new LongRestTaken(initiator, healedAmounts, selectedFood));
        return new RestOutcome.Rested(healedAmounts);
    }
}
