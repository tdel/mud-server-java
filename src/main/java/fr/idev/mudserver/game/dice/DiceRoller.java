package fr.idev.mudserver.game.dice;

import java.security.SecureRandom;
import java.util.Random;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.Skill;

@Component
public class DiceRoller {

    private static final int[] SIMULATED_SIDES = {2, 3};

    private final Random random = new SecureRandom();

    public DiceRoll roll(String expression) {
        return roll(DiceExpression.parse(expression));
    }

    /**
     * Tirage de probabilité indépendant (0 à 1), utilisé pour les tables de butin
     * ({@code game.actor.LootService}) plutôt qu'une notation de dés — réutilise le
     * même {@link Random} que {@link #roll}, pas de source d'aléa parallèle.
     */
    public boolean rollChance(double probability) {
        return random.nextDouble() < probability;
    }

    public DiceRoll roll(DiceExpression expression) {
        int[] rolls = new int[expression.count()];
        for (int i = 0; i < expression.count(); i++) {
            rolls[i] = rollDie(expression.sides());
        }
        return new DiceRoll(rolls, expression.modifier());
    }

    /**
     * Jet de d20 unique DnD5e, avec ou sans désavantage (2d20, garde le plus bas —
     * pas de variante avantage pour l'instant, aucun appelant n'en a besoin).
     * Retourne toujours un {@link DiceRoll} à un seul dé dans {@code rolls()} (le
     * d20 finalement retenu) : {@link DiceRoll#total()} ne double donc jamais le
     * résultat même quand deux d20 sont physiquement lancés en interne, et les
     * appelants qui lisent {@code rolls()[0]} comme jet naturel (règle du 1/20
     * naturel côté {@code game.CombatService}) restent valides sans changement.
     */
    public DiceRoll rollD20(int modifier, boolean disadvantage) {
        int kept = disadvantage ? Math.min(rollDie(20), rollDie(20)) : rollDie(20);
        return new DiceRoll(new int[]{kept}, modifier);
    }

    /**
     * Résout un jet de compétence DnD5e : 1d20 + modificateur de la caractéristique
     * gouvernante, + bonus de maîtrise si le personnage est proficient sur cette
     * compétence (voir {@link GamePlayer#getSkillProficiencies()}, résolues une
     * fois pour toutes à la construction du personnage), comparé à une DC fournie
     * par l'appelant. Contrairement à {@code game.CombatService#resolveHit}, aucune
     * règle de critique sur 1/20 naturel : en DnD5e RAW cette règle est propre aux
     * jets d'attaque, pas aux jets de compétence/sauvegarde génériques.
     */
    public CheckResult check(GamePlayer character, Skill skill, int dc) {
        boolean proficient = character.getSkillProficiencies().contains(skill);
        return checkOrSave(character, skill.getGoverningAttribute(), proficient, dc, skill.label());
    }

    /**
     * Résout un jet de sauvegarde DnD5e — même mécanique que {@link #check}, mais
     * la maîtrise vient de {@link GamePlayer#getSavingThrowProficiencies()} plutôt
     * que des compétences.
     */
    public CheckResult save(GamePlayer character, Attribute attribute, int dc) {
        boolean proficient = character.getSavingThrowProficiencies().contains(attribute);
        return checkOrSave(character, attribute, proficient, dc, attribute.label());
    }

    private CheckResult checkOrSave(GamePlayer character, Attribute attribute, boolean proficient, int dc,
            String label) {
        int modifier = character.getModifier(attribute) + (proficient ? character.getProficiencyBonus() : 0);
        boolean disadvantage = (attribute == Attribute.STRENGTH || attribute == Attribute.DEXTERITY)
                && character.isWearingNonProficientArmor();
        DiceRoll diceRoll = rollD20(modifier, disadvantage);
        boolean success = resolveCheck(diceRoll.total(), dc);
        return new CheckResult(label, diceRoll.total(), dc, proficient, disadvantage, success);
    }

    static boolean resolveCheck(int total, int dc) {
        return total >= dc;
    }

    private int rollDie(int sides) {
        if (sides == 100) {
            return rollPercentile();
        }

        for (int simulatedSides : SIMULATED_SIDES) {
            if (sides == simulatedSides) {
                // d2/d3 don't exist physically: roll a die with double the sides
                // and halve the result, rounded up.
                return (int) Math.ceil(randomInt(1, sides * 2) / 2.0);
            }
        }

        return randomInt(1, sides);
    }

    private int rollPercentile() {
        // 2d10: one for the tens digit (0-9), one for the units (0-9) - not
        // a sum. Double 0 is 100, there is no 0 result on a d100.
        int tens = randomInt(0, 9);
        int units = randomInt(0, 9);
        int result = tens * 10 + units;
        return result == 0 ? 100 : result;
    }

    private int randomInt(int minInclusive, int maxInclusive) {
        return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
    }
}
