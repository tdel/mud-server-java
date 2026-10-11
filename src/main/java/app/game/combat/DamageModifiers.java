package app.game.combat;

import app.domain.SkillElement;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.Attribute;
import app.domain.actor.CreatureRace;
import app.domain.actor.ModifiedStat;
import app.domain.item.WeaponType;
import app.game.Randomizer;

// Modificateurs L2 des dégâts physiques (attaque normale et compétences) issus
// des compétences de profession : critiques (Vicious Stance, Death Whisper,
// Heavy Armor Mastery), faiblesses de race (Detect Animal Weakness), attaque
// sacrée (Holy Weapon), résistance aux flèches (Deflect Arrow), blocage au
// bouclier (Shield Mastery). Fonctions pures hors tirages via Randomizer.
public final class DamageModifiers {

    // Blocage au bouclier : chance de base (%) d'un bouclier équipé (rShld des
    // boucliers L2, tous à 20), modulée par la DEX du porteur (calcShldUse) ; la
    // défense du bouclier (sDef) s'ajoute alors à sa p.def.
    public static final int BASE_SHIELD_RATE = 20;

    private DamageModifiers() {
    }

    // Chance (0-1) de critique physique de l'attaquant contre ce défenseur :
    // P.Crit. (millièmes, comme en L2) réduit par CRIT_RATE_RECEIVED (Light Armor
    // Mastery du Rogue).
    public static double physicalCritChance(AbstractCharacter attacker, AbstractCharacter defender) {
        double base = CombatFormulas.critChance(attacker.getStatSystem().getEffective(ModifiedStat.PCRIT));
        return base * defender.getStatSystem().adjust(ModifiedStat.CRIT_RATE_RECEIVED, 1000) / 1000.0;
    }

    // p.def du défenseur, augmentée de la défense du bouclier s'il bloque.
    public static int defenseWithBlock(AbstractCharacter defender) {
        int pDef = defender.getStatSystem().getEffective(ModifiedStat.PDEF);
        if (!defender.getSkillSystem().hasShield()) {
            return pDef;
        }
        double rate = defender.getStatSystem().adjust(ModifiedStat.SHIELD_RATE, BASE_SHIELD_RATE)
                * Attribute.DEX.bonus(defender.getAttributeSystem().getAttribute(Attribute.DEX));
        if (!Randomizer.rollChance(Math.min(rate, 90) / 100.0)) {
            return pDef;
        }
        int shieldDef = defender.getInventorySystem().getEquippedItems().stream()
                .filter(item -> item.getSlot() == app.domain.item.EquipmentSlot.OFF_HAND)
                .mapToInt(app.domain.item.Item::getPDef).sum();
        return pDef + defender.getStatSystem().adjust(ModifiedStat.SHIELD_DEF, shieldDef);
    }

    // Dégâts physiques bruts (déjà passés par CombatFormulas.resolvePhysicalDamage)
    // -> dégâts finaux.
    public static int applyPhysical(AbstractCharacter attacker, AbstractCharacter defender, int damage,
            boolean critical) {
        double amount = damage;
        if (critical) {
            amount = attacker.getStatSystem().adjust(ModifiedStat.CRIT_DAMAGE, (int) Math.round(amount))
                    + attacker.getStatSystem().adjust(ModifiedStat.CRIT_DAMAGE_ADD, 0);
            amount = defender.getStatSystem().adjust(ModifiedStat.CRIT_DAMAGE_RECEIVED, (int) Math.round(amount));
        }
        amount = applyRaceBonus(attacker, defender, (int) Math.round(amount));
        int holy = attacker.getStatSystem().adjust(ModifiedStat.ATTACK_HOLY, 0);
        if (holy > 0) {
            // Attaque sacrée : bonus de `holy` % réduit (ou amplifié, morts-vivants)
            // par la résistance sacrée de la cible.
            amount = CombatFormulas.applyElementalResistance((int) Math.round(amount),
                    defender.elementalResistance(SkillElement.HOLY) - holy);
        }
        if (attacker.getSkillSystem().wieldedWeapon() == WeaponType.BOW) {
            int bowResist = defender.getStatSystem().adjust(ModifiedStat.BOW_RESIST, 0);
            amount = amount * Math.max(0, 100 - bowResist) / 100.0;
        }
        return Math.max(1, (int) Math.round(amount));
    }

    // Dégâts magiques : faiblesses de race seulement (les critiques magiques ont
    // leur propre multiplicateur dans resolveMagicalDamage).
    public static int applyMagical(AbstractCharacter attacker, AbstractCharacter defender, int damage) {
        return Math.max(1, applyRaceBonus(attacker, defender, damage));
    }

    private static int applyRaceBonus(AbstractCharacter attacker, AbstractCharacter defender, int amount) {
        CreatureRace race = defender.getCreatureRace();
        if (race == CreatureRace.ANIMAL || race == CreatureRace.BEAST) {
            return attacker.getStatSystem().adjust(ModifiedStat.DMG_VS_ANIMAL, amount);
        }
        if (race == CreatureRace.BUG) {
            return attacker.getStatSystem().adjust(ModifiedStat.DMG_VS_BUG, amount);
        }
        return amount;
    }

    // Reflect Damage / Blazing Skin : part (%) des dégâts de mêlée renvoyée à
    // l'attaquant.
    public static int reflected(AbstractCharacter defender, int damage) {
        int percent = defender.getStatSystem().adjust(ModifiedStat.REFLECT_DAMAGE, 0);
        return percent <= 0 ? 0 : damage * percent / 100;
    }
}
