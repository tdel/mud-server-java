package fr.idev.mudserver.domain;

import java.util.UUID;

import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.actor.event.GamePlayerUsedPotion;
import fr.idev.mudserver.game.dice.DiceRoller;

/**
 * {@link ItemTemplate} dont on peut faire {@code use <nom>} — {@code consume}
 * teste le type d'effet et exécute le comportement correspondant ; un futur
 * second type (poison, etc.) ajoutera sa propre branche sans toucher au reste
 * du pipeline (voir {@code ItemService.warmItemTemplates}, {@code
 * CombatEngine.useItem}).
 *
 * <p>
 * Contrairement à
 * {@link fr.idev.mudserver.domain.actor.GamePlayer}/{@link Item}/{@link Room},
 * ce type n'est jamais reconstruit depuis une ligne DB par un DAO — il n'existe
 * qu'une fois, construit par {@code
 * ItemService.warmItemTemplates()} (un {@code @Service}) au démarrage. Recevoir
 * un {@link DiceRoller} au constructeur n'est donc pas une entorse à la règle
 * "POJO sans bean" du domaine : {@code warmItemTemplates()} a déjà accès à
 * l'injection de dépendances normale, contrairement aux DAO qui construisent
 * {@code GamePlayer}/{@code Item}/{@code Room} hors de tout contexte Spring.
 */
public class ConsumableItem extends ItemTemplate {

    private final ConsumableEffect effect;
    private final String effectDice;
    private final DiceRoller diceRoller;

    public ConsumableItem(UUID id, String name, String description, ItemType type, int weight,
            ArmorCategory armorCategory, int baseAc, String damageDice, WeaponCategory weaponCategory, int price,
            Rarity rarity, int bonus, ConsumableEffect effect, String effectDice, DiceRoller diceRoller) {
        super(id, name, description, type, weight, armorCategory, baseAc, damageDice, weaponCategory, price, rarity,
                bonus);
        this.effect = effect;
        this.effectDice = effectDice;
        this.diceRoller = diceRoller;
    }

    public void consume(GamePlayer character, Item item) {
        switch (effect) {
            case HEALING -> heal(character, item);
        }
    }

    private void heal(GamePlayer character, Item item) {
        int amount = diceRoller.roll(effectDice).total();
        int healed = Math.min(amount, character.getMaxHealth() - character.getCurrentHealth());
        character.setCurrentHealth(character.getCurrentHealth() + healed);
        character.getInventory().removeItem(item);
        DomainEventPublisher.publish(new GamePlayerUsedPotion(character, item, healed));
    }
}
