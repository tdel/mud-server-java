package fr.idev.mudserver.game;

import java.util.Optional;

import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.Attribute;
import fr.idev.mudserver.domain.EquipmentSlot;
import fr.idev.mudserver.domain.GameMonster;
import fr.idev.mudserver.domain.GamePlayer;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.game.dice.DiceExpression;
import fr.idev.mudserver.game.dice.DiceRoll;
import fr.idev.mudserver.game.dice.DiceRoller;
import fr.idev.mudserver.network.message.ingame.MonsterDefeated;

/**
 * Résout une attaque au corps-à-corps selon les règles DnD5e : jet d'attaque
 * (1d20 + modificateur de FOR + bonus de maîtrise) comparé à la CA de la cible,
 * dégâts de l'arme équipée (ou à mains nues) + modificateur de FOR en cas de
 * réussite. Le monstre encaisse via {@link GameMonster#takeDamage}, seul point
 * d'entrée qui protège la mutation des PV contre deux attaquants concurrents
 * (voir sa Javadoc). Aucune riposte du monstre pour l'instant — combat à sens
 * unique, voir CLAUDE.md/la conversation d'origine.
 */
@Service
public class CombatService {

    private final DiceRoller diceRoller;

    public CombatService(DiceRoller diceRoller) {
        this.diceRoller = diceRoller;
    }

    public CombatResult attack(GamePlayer attacker, GameMonster target) {
        int strengthModifier = attacker.getModifier(Attribute.STRENGTH);
        int attackBonus = strengthModifier + attacker.getProficiencyBonus();

        DiceRoll attackRoll = diceRoller.roll(new DiceExpression(1, 20, attackBonus));
        int naturalRoll = attackRoll.rolls()[0];
        boolean criticalHit = naturalRoll == 20;
        int armorClass = target.getArmorClass();
        boolean hit = resolveHit(naturalRoll, attackRoll.total(), armorClass);

        if (!hit) {
            return new CombatResult(target.getName(), false, false, attackRoll.total(), armorClass, 0,
                    target.getCurrentHealth(), false);
        }

        int damage = rollDamage(attacker, strengthModifier, criticalHit);
        boolean defeated = target.takeDamage(damage);

        if (defeated) {
            attacker.getCurrentRoom().removeMonster(target);
            attacker.setTarget(null);
            attacker.getCurrentRoom().broadcast(new MonsterDefeated(target.getName()), attacker);
        }

        return new CombatResult(target.getName(), true, criticalHit, attackRoll.total(), armorClass, damage,
                target.getCurrentHealth(), defeated);
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
