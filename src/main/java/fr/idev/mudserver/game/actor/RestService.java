package fr.idev.mudserver.game.actor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.FoodItem;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.actor.event.LongRestTaken;
import fr.idev.mudserver.domain.actor.event.ShortRestTaken;
import fr.idev.mudserver.game.GameWorld;

/**
 * Orchestre un repos court/long — contrairement aux mutations habituelles
 * portées par une seule méthode {@link GamePlayer}, un repos affecte
 * simultanément tous les joueurs actuellement en ligne dans la même
 * {@code WorldInstance} que l'initiateur
 * ({@link GameWorld#onlineCharactersInWorldInstance(java.util.UUID)}), donc
 * aucun objet de domaine unique ne peut porter cette logique.
 * {@code game.CombatEngine} est le précédent dans ce projet pour un
 * {@code @Service} qui mute directement plusieurs {@code GameCharacter} avant
 * de publier un événement.
 *
 * <p>
 * Chaque méthode mute l'état en mémoire (PV via {@link GamePlayer#heal},
 * compteur de repos courts) puis publie un seul événement portant le résultat
 * pour tous les joueurs affectés — {@code game.actor.CharacterService} persiste
 * et notifie chaque joueur, {@code game.ItemService} supprime en plus les
 * provisions consommées pour {@link LongRestTaken}. Ni {@code shortRest} ni
 * {@code longRest} ne publient d'événement en cas d'échec (combat en cours,
 * repos courts épuisés, provisions insuffisantes) : rien n'est muté.
 */
@Service
public class RestService {

    private static final Logger log = LoggerFactory.getLogger(RestService.class);

    /**
     * Valeur nutritionnelle cumulée minimale exigée pour déclencher un repos long —
     * le joueur peut sélectionner plus que nécessaire, l'excédent est tout de même
     * consommé (voir {@code controller.ingame.Rest}).
     */
    public static final int LONG_REST_PROVISION_THRESHOLD = 20;

    private final GameWorld gameWorld;

    public RestService(GameWorld gameWorld) {
        this.gameWorld = gameWorld;
    }

    public sealed interface RestOutcome {

        record Rested(Map<GamePlayer, Integer> healedAmounts) implements RestOutcome {
        }

        record InCombat() implements RestOutcome {
        }

        record NoShortRestLeft() implements RestOutcome {
        }

        record NotEnoughProvisions(int totalValue) implements RestOutcome {
        }
    }

    /**
     * Refuse si {@code initiator} est en combat ou a déjà pris
     * {@link GamePlayer#MAX_SHORT_RESTS_BEFORE_LONG_REST} repos courts depuis le
     * dernier repos long. Sinon, chaque joueur en ligne de la même
     * {@code WorldInstance} (initiateur compris) regagne
     * {@code hitDie/2 + 1 + modificateur CON} PV — même formule que le gain de PV
     * au level-up ({@code CharacterService.onCharacterGainedXp}), et voit son
     * propre compteur de repos courts incrémenté.
     */
    public RestOutcome shortRest(GamePlayer initiator) {
        if (initiator.isInCombat()) {
            return new RestOutcome.InCombat();
        }
        if (!initiator.canTakeShortRest()) {
            return new RestOutcome.NoShortRestLeft();
        }

        Map<GamePlayer, Integer> healedAmounts = new LinkedHashMap<>();
        for (GamePlayer character : gameWorld.onlineCharactersInWorldInstance(initiator.getWorldInstanceId())) {
            int hitDie = character.getCharacterClass().hitDie();
            int constitutionModifier = character.getModifier(Attribute.CONSTITUTION);
            int amount = Math.max(1, hitDie / 2 + 1 + constitutionModifier);
            healedAmounts.put(character, character.heal(amount));
            character.incrementShortRestCount();
        }

        log.info("rest.short_rest_taken initiator={} affected={}", initiator.getName(), healedAmounts.size());
        DomainEventPublisher.publish(new ShortRestTaken(initiator, healedAmounts));
        return new RestOutcome.Rested(healedAmounts);
    }

    /**
     * Refuse si {@code initiator} est en combat ou si la somme des
     * {@link FoodItem#getNutritionValue()} de {@code selectedFood} est sous
     * {@link #LONG_REST_PROVISION_THRESHOLD} — dans ces deux cas, rien n'est muté
     * ni consommé. Sinon, {@code selectedFood} est retiré de l'inventaire de
     * {@code initiator} (détruit, voir {@code ItemService#onLongRestTaken}) et
     * chaque joueur en ligne de la même {@code WorldInstance} regagne tous ses PV
     * manquants, compteur de repos courts remis à zéro.
     */
    public RestOutcome longRest(GamePlayer initiator, List<Item> selectedFood) {
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

        Map<GamePlayer, Integer> healedAmounts = new LinkedHashMap<>();
        for (GamePlayer character : gameWorld.onlineCharactersInWorldInstance(initiator.getWorldInstanceId())) {
            healedAmounts.put(character, character.heal(character.getMaxHealth()));
            character.resetShortRestCount();
        }

        log.info("rest.long_rest_taken initiator={} affected={} provisionsConsumed={}", initiator.getName(),
                healedAmounts.size(), selectedFood.size());
        DomainEventPublisher.publish(new LongRestTaken(initiator, healedAmounts, selectedFood));
        return new RestOutcome.Rested(healedAmounts);
    }
}
