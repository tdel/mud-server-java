package fr.idev.mudserver.game;

import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.Skill;
import fr.idev.mudserver.game.dice.DiceExpression;
import fr.idev.mudserver.game.dice.DiceRoll;
import fr.idev.mudserver.game.dice.DiceRoller;

/**
 * Résout un jet de compétence ({@link #check}) ou de sauvegarde ({@link #save})
 * DnD5e : 1d20 + modificateur de la caractéristique gouvernante, + bonus de
 * maîtrise si le personnage est proficient sur cette compétence/caractéristique
 * (voir {@link GamePlayer#getSkillProficiencies()}/
 * {@link GamePlayer#getSavingThrowProficiencies()}, résolues une fois pour
 * toutes à la construction du personnage), comparé à une DC fournie par
 * l'appelant. Contrairement à {@link CombatService#resolveHit}, aucune règle de
 * critique sur 1/20 naturel : en DnD5e RAW cette règle est propre aux jets
 * d'attaque, pas aux jets de compétence/sauvegarde génériques.
 */
@Service
public class CheckService {

    private final DiceRoller diceRoller;

    public CheckService(DiceRoller diceRoller) {
        this.diceRoller = diceRoller;
    }

    public CheckResult check(GamePlayer character, Skill skill, int dc) {
        boolean proficient = character.getSkillProficiencies().contains(skill);
        return roll(character, skill.getGoverningAttribute(), proficient, dc, skill.label());
    }

    public CheckResult save(GamePlayer character, Attribute attribute, int dc) {
        boolean proficient = character.getSavingThrowProficiencies().contains(attribute);
        return roll(character, attribute, proficient, dc, attribute.label());
    }

    private CheckResult roll(GamePlayer character, Attribute attribute, boolean proficient, int dc, String label) {
        int modifier = character.getModifier(attribute) + (proficient ? character.getProficiencyBonus() : 0);
        DiceRoll diceRoll = diceRoller.roll(new DiceExpression(1, 20, modifier));
        boolean success = resolveCheck(diceRoll.total(), dc);
        return new CheckResult(label, diceRoll.total(), dc, proficient, success);
    }

    static boolean resolveCheck(int total, int dc) {
        return total >= dc;
    }
}
