package fr.idev.mudserver.game;

import java.util.Optional;

import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.EquipmentSlot;
import fr.idev.mudserver.domain.actor.GameMonster;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.game.dice.DiceExpression;
import fr.idev.mudserver.game.dice.DiceRoll;
import fr.idev.mudserver.game.dice.DiceRoller;

/**
 * Résout uniquement la phase « jet d'attaque + jet de dégâts » d'une attaque au
 * corps-à-corps selon les règles DnD5e : jet d'attaque (1d20 + modificateur de
 * FOR + bonus de maîtrise) comparé à la CA de la cible, dégâts de l'arme
 * équipée (ou à mains nues) + modificateur de FOR en cas de réussite.
 * {@code tryAttack} ne touche jamais aux PV du monstre — l'appelant
 * ({@code controller.ingame.Attack}) applique lui-même les dégâts via
 * {@link GameMonster#takeDamage}, qui gère seul la mutation concurrente des PV
 * et la publication de {@code CharacterDied} sur mise à mort (voir sa Javadoc).
 * Cette séparation garde cette classe pure et testable en unitaire, sans
 * dépendre d'un contexte Spring. Aucune riposte du monstre pour l'instant —
 * combat à sens unique, voir CLAUDE.md/la conversation d'origine.
 */
@Service
public class CombatService {

    private final DiceRoller diceRoller;

    public CombatService(DiceRoller diceRoller) {
        this.diceRoller = diceRoller;
    }

    public CombatResult tryAttack(GamePlayer attacker, GameMonster target) {
        int strengthModifier = attacker.getModifier(Attribute.STRENGTH);
        int attackBonus = strengthModifier + attacker.getProficiencyBonus();

        DiceRoll attackRoll = diceRoller.roll(new DiceExpression(1, 20, attackBonus));
        int naturalRoll = attackRoll.rolls()[0];
        boolean criticalHit = naturalRoll == 20;
        int armorClass = target.getArmorClass();
        boolean hit = resolveHit(naturalRoll, attackRoll.total(), armorClass);

        if (!hit) {
            return new CombatResult(target.getName(), false, false, attackRoll.total(), armorClass, 0);
        }

        int damage = rollDamage(attacker, strengthModifier, criticalHit);
        return new CombatResult(target.getName(), true, criticalHit, attackRoll.total(), armorClass, damage);
    }

    /**
     * Règle DnD5e du jet d'attaque, extraite en méthode pure pour être testable
     * sans dépendre du RNG réel : un 1 naturel est toujours un échec, un 20 naturel
     * toujours une réussite, sinon on compare le total à la CA.
     */
    static boolean resolveHit(int naturalRoll, int totalRoll, int armorClass) {
        if (naturalRoll == 1) {
            return false;
        }
        if (naturalRoll == 20) {
            return true;
        }
        return totalRoll >= armorClass;
    }

    private int rollDamage(GamePlayer attacker, int strengthModifier, boolean criticalHit) {
        Optional<Item> weapon = attacker.getEquippedItems().stream()
                .filter(item -> item.getSlot() == EquipmentSlot.WEAPON).findFirst();

        if (weapon.isEmpty()) {
            // Attaque à mains nues (SRD) : 1 + modificateur de FOR, pas de dé donc rien à
            // doubler en cas de critique.
            return Math.max(0, 1 + strengthModifier);
        }

        DiceExpression base = DiceExpression.parse(weapon.get().getDamageDice());
        int diceCount = criticalHit ? base.count() * 2 : base.count();
        return Math.max(0, diceRoller.roll(new DiceExpression(diceCount, base.sides(), strengthModifier)).total());
    }
}
