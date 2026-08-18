package fr.idev.mudserver.domain.actor.system;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import fr.idev.mudserver.config.GameConfig;
import fr.idev.mudserver.domain.actor.component.CombatComponent;
import fr.idev.mudserver.domain.actor.component.RestComponent;
import fr.idev.mudserver.domain.actor.component.WorldComponent;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.actor.event.LongRestTaken;
import fr.idev.mudserver.domain.actor.event.ShortRestTaken;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.instance.RestOutcome;
import fr.idev.mudserver.domain.item.FoodItem;
import fr.idev.mudserver.domain.item.Item;

@Service
public class RestSystem {

    private final InventorySystem inventorySystem;
    private final CombatSystem combatSystem;
    private final LevelingSystem levelingSystem;
    private final EncounterSystem encounterSystem;

    public RestSystem(InventorySystem inventorySystem, CombatSystem combatSystem, LevelingSystem levelingSystem,
            EncounterSystem encounterSystem) {
        this.inventorySystem = inventorySystem;
        this.combatSystem = combatSystem;
        this.levelingSystem = levelingSystem;
        this.encounterSystem = encounterSystem;
    }

    public boolean canTakeShortRest(CharacterInstance character) {
        return component(character).shortRestCount < CharacterInstance.MAX_SHORT_RESTS_BEFORE_LONG_REST;
    }

    public void incrementShortRestCount(CharacterInstance character) {
        synchronized (character) {
            component(character).shortRestCount += 1;
        }
    }

    public void resetShortRestCount(CharacterInstance character) {
        synchronized (character) {
            component(character).shortRestCount = 0;
        }
    }

    public RestOutcome doShortRest(CharacterInstance initiator) {
        if (encounterSystem.isInCombat(initiator)) {
            return new RestOutcome.InCombat();
        }
        if (!canTakeShortRest(initiator)) {
            return new RestOutcome.NoShortRestLeft();
        }

        Map<CharacterInstance, Integer> healedAmounts = new LinkedHashMap<>();
        for (CharacterInstance character : initiator.component(WorldComponent.class).worldInstance.onlineCharacters()) {
            int amount = levelingSystem.hitDieRecovery(character);
            healedAmounts.put(character, combatSystem.heal(character, amount));
            incrementShortRestCount(character);
        }

        DomainEventPublisher.publish(new ShortRestTaken(initiator, healedAmounts));
        return new RestOutcome.Rested(healedAmounts);
    }

    public RestOutcome doLongRest(CharacterInstance initiator, List<Item> selectedFood) {
        if (encounterSystem.isInCombat(initiator)) {
            return new RestOutcome.InCombat();
        }

        int totalValue = selectedFood.stream().mapToInt(item -> ((FoodItem) item.getTemplate()).getNutritionValue())
                .sum();
        if (totalValue < GameConfig.LONG_REST_PROVISION_THRESHOLD) {
            return new RestOutcome.NotEnoughProvisions(totalValue);
        }

        for (Item food : selectedFood) {
            inventorySystem.removeItem(initiator, food);
        }

        Map<CharacterInstance, Integer> healedAmounts = new LinkedHashMap<>();
        for (CharacterInstance character : initiator.component(WorldComponent.class).worldInstance.onlineCharacters()) {
            healedAmounts.put(character,
                    combatSystem.heal(character, character.component(CombatComponent.class).maxHealth));
            resetShortRestCount(character);
        }

        DomainEventPublisher.publish(new LongRestTaken(initiator, healedAmounts, selectedFood));
        return new RestOutcome.Rested(healedAmounts);
    }

    private RestComponent component(CharacterInstance character) {
        return character.component(RestComponent.class);
    }
}
