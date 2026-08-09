package fr.idev.mudserver.game;

import java.util.Optional;

import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.EquipmentSlot;
import fr.idev.mudserver.domain.actor.GameCharacter;
import fr.idev.mudserver.domain.actor.GameMonster;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.game.dice.DiceExpression;
import fr.idev.mudserver.game.dice.DiceRoll;
import fr.idev.mudserver.game.dice.DiceRoller;

/**
 * Résout la phase « jet d'attaque + jet de dégâts » d'une attaque au
 * corps-à-corps selon les règles DnD5e, dans les deux sens : {@link #tryAttack}
 * (joueur vers monstre — 1d20 + modificateur de FOR + bonus de maîtrise, dégâts
 * de l'arme équipée ou à mains nues) et {@link #tryMonsterAttack} (monstre vers
 * joueur — même mécanique, bonus de maîtrise fixe +2 en l'absence de notion de
 * CR/progression sur {@link MonsterTemplate}, dégâts
 * {@link MonsterTemplate#getNaturalDamageDice()}). Ni l'une ni l'autre ne
 * touchent aux PV de la cible — l'appelant ({@code game.CombatEngine}) applique
 * lui-même les dégâts via
 * {@link GameMonster#takeDamage}/{@link GamePlayer#takeDamage}, qui gèrent
 * seuls la mutation concurrente des PV et la publication de
 * {@code CharacterDied}/{@code GamePlayerDied} sur mise à mort (voir leurs
 * Javadoc). Cette séparation garde cette classe pure et testable en unitaire,
 * sans dépendre d'un contexte Spring.
 */
@Service
public class CombatService {

    private final DiceRoller diceRoller;

    public CombatService(DiceRoller diceRoller) {
        this.diceRoller = diceRoller;
    }

    public CombatResult tryAttack(GamePlayer attacker, GameMonster target) {
        Optional<Item> weapon = attacker.getInventory().getEquippedItems().stream()
                .filter(item -> item.getSlot() == EquipmentSlot.WEAPON).findFirst();
        int weaponBonus = weapon.map(Item::getBonus).orElse(0);
        boolean weaponProficient = weapon
                .map(item -> attacker.getWeaponProficiencies().contains(item.getWeaponCategory())).orElse(true);

        int strengthModifier = attacker.getModifier(Attribute.STRENGTH);
        int attackBonus = strengthModifier + (weaponProficient ? attacker.getProficiencyBonus() : 0) + weaponBonus;
        boolean disadvantage = attacker.isWearingNonProficientArmor();

        DiceRoll attackRoll = diceRoller.rollD20(attackBonus, disadvantage);
        int naturalRoll = attackRoll.rolls()[0];
        boolean criticalHit = naturalRoll == 20;
        int armorClass = target.getArmorClass();
        boolean hit = resolveHit(naturalRoll, attackRoll.total(), armorClass);

        if (!hit) {
            return new CombatResult(target.getName(), false, false, attackRoll.total(), armorClass, 0, disadvantage);
        }

        int damage = rollDamage(weapon, strengthModifier, criticalHit);
        return new CombatResult(target.getName(), true, criticalHit, attackRoll.total(), armorClass, damage,
                disadvantage);
    }

    /**
     * Pendant de {@link #tryAttack} pour la riposte du monstre. +2 fixe en guise de
     * bonus de maîtrise : équivalent d'un joueur niveau 1, choisi plutôt que
     * d'ajouter un champ CR/maîtrise à {@link MonsterTemplate} pour cette itération
     * — les monstres n'ont pas de progression de niveau modélisée.
     */
    public CombatResult tryMonsterAttack(GameMonster attacker, GamePlayer target) {
        int strengthModifier = attacker.getModifier(Attribute.STRENGTH);
        int attackBonus = strengthModifier + 2;

        DiceRoll attackRoll = diceRoller.roll(new DiceExpression(1, 20, attackBonus));
        int naturalRoll = attackRoll.rolls()[0];
        boolean criticalHit = naturalRoll == 20;
        int armorClass = target.getArmorClass();
        boolean hit = resolveHit(naturalRoll, attackRoll.total(), armorClass);

        if (!hit) {
            return new CombatResult(target.getName(), false, false, attackRoll.total(), armorClass, 0, false);
        }

        int damage = rollMonsterDamage(attacker, strengthModifier, criticalHit);
        return new CombatResult(target.getName(), true, criticalHit, attackRoll.total(), armorClass, damage, false);
    }

    /**
     * Jet d'initiative DnD5e standard (1d20 + modificateur de DEX), commun aux
     * joueurs et aux monstres puisque tous deux sont des {@link GameCharacter}.
     * Consommé par
     * {@code game.CombatEngine}/{@code domain.actor.CombatEncounter#establishInitiativeOrder}.
     */
    public int rollInitiative(GameCharacter character) {
        int dexterityModifier = character.getModifier(Attribute.DEXTERITY);
        return diceRoller.roll(new DiceExpression(1, 20, dexterityModifier)).total();
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

    private int rollDamage(Optional<Item> weapon, int strengthModifier, boolean criticalHit) {
        if (weapon.isEmpty()) {
            // Attaque à mains nues (SRD) : 1 + modificateur de FOR, pas de dé donc rien à
            // doubler en cas de critique.
            return Math.max(0, 1 + strengthModifier);
        }

        DiceExpression base = DiceExpression.parse(weapon.get().getDamageDice());
        int diceCount = criticalHit ? base.count() * 2 : base.count();
        int modifier = strengthModifier + weapon.get().getBonus();
        return Math.max(0, diceRoller.roll(new DiceExpression(diceCount, base.sides(), modifier)).total());
    }

    private int rollMonsterDamage(GameMonster attacker, int strengthModifier, boolean criticalHit) {
        DiceExpression base = DiceExpression.parse(attacker.getNaturalDamageDice());
        int diceCount = criticalHit ? base.count() * 2 : base.count();
        return Math.max(0, diceRoller.roll(new DiceExpression(diceCount, base.sides(), strengthModifier)).total());
    }
}
