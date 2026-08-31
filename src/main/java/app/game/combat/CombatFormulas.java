package app.game.combat;

import java.time.Duration;

import app.domain.item.ArmorCategory;

/**
 * Formules de combat façon Lineage2 : stats dérivées (p.atk/p.def/m.atk/m.def
 * /accuracy/evasion/criticalRate) et résolution d'un coup (touche, critique,
 * dégâts). Reconstruites à partir des mécaniques largement documentées par la
 * communauté (notamment le moteur open-source L2J) — les constantes ci-dessous
 * sont un point de départ à équilibrer en jeu, pas une reproduction garantie du
 * client retail.
 *
 * <p>
 * Fonctions pures, sans dépendance Spring ni RNG : le tirage aléatoire réel
 * (hit/critique/variance) reste à la charge de l'appelant, via
 * {@code app.game.dice.DiceRoller}.
 *
 * <p>
 * Constantes calibrées par simulation déterministe (espérance de dégâts, pas de
 * tirages réels) sur un Fighter/Mystic niveau 1 fraîchement créé (arme de
 * départ, sans armure) contre chaque monstre de {@code data/monsters
 * .json} : viser un hitChance en miroir ~65-70%, une marge de temps-de-mise-
 * à-mort favorable au joueur sur les monstres "starter" (même niveau, faible
 * récompense XP), et une difficulté réelle sur les monstres au-dessus du niveau
 * du joueur. Point de départ à retester après tout changement de contenu
 * (nouvelles armes/armures, nouveaux monstres).
 */
public final class CombatFormulas {

    public static final double STAT_BONUS_BASE = 1.03;
    public static final int NEUTRAL_SCORE = 10;
    public static final double LEVEL_FACTOR_PER_LEVEL = 0.02;
    public static final int UNARMED_PATK = 4;
    public static final double BASE_DEF_FACTOR = 6.0;
    public static final double ACCURACY_FACTOR = 3.0;
    public static final double EVASION_FACTOR = 3.0;
    public static final int BASE_ACCURACY = 45;
    public static final int BASE_EVASION = 15;
    public static final int BASE_CRIT_RATE = 8;
    public static final int MIN_CRIT_RATE = 1;
    public static final int MAX_CRIT_RATE = 90;
    public static final double MIN_HIT_CHANCE = 0.20;
    public static final double MAX_HIT_CHANCE = 0.98;
    public static final double CRITICAL_MULTIPLIER = 2.0;
    public static final double ELEMENT_RESIST_FACTOR = 0.01;
    public static final double MIN_ELEMENT_MULTIPLIER = 0.1;
    public static final double MAX_ELEMENT_MULTIPLIER = 2.0;
    public static final int ENCHANT_ATK_BONUS_PER_LEVEL = 2;
    public static final int ENCHANT_DEF_BONUS_PER_LEVEL = 1;
    public static final int BASE_DEBUFF_RESIST = 5;
    public static final double DEBUFF_RESIST_FACTOR = 3.0;
    public static final int MAX_DEBUFF_RESIST = 70;
    public static final double HP_REGEN_RATE = 0.02;
    public static final double MP_REGEN_RATE = 0.02;
    public static final int BASE_ATK_SPD = 300;
    public static final double ATK_SPD_DELAY_CONSTANT = 500_000.0;

    private CombatFormulas() {
    }

    public static double statBonus(int score) {
        return Math.pow(STAT_BONUS_BASE, score - NEUTRAL_SCORE);
    }

    public static double levelFactor(int level) {
        return 1.0 + (level - 1) * LEVEL_FACTOR_PER_LEVEL;
    }

    public static int physicalAttack(int weaponPAtk, int strengthScore, int level) {
        return (int) Math.round(weaponPAtk * statBonus(strengthScore) * levelFactor(level));
    }

    public static int magicalAttack(int weaponMAtk, int intelligenceScore, int level) {
        return (int) Math.round(weaponMAtk * statBonus(intelligenceScore) * levelFactor(level));
    }

    public static int physicalDefense(int armorPDefSum, int constitutionScore) {
        return Math.max(1, (int) Math.round(armorPDefSum + statBonus(constitutionScore) * BASE_DEF_FACTOR));
    }

    public static int magicalDefense(int armorMDefSum, int menScore) {
        return Math.max(1, (int) Math.round(armorMDefSum + statBonus(menScore) * BASE_DEF_FACTOR));
    }

    // Pas d'équivalent "objet" pour HP/mana dans ce projet (contrairement à
    // p.atk/p.def) : le niveau doit rester le moteur direct de la croissance, d'où
    // un facteur linéaire en level plutôt que le levelFactor() ci-dessus
    // (+2%/niveau
    // seulement, calibré pour un système où l'équipement porte la progression).
    public static int maxHealth(int hitDie, int constitutionScore, int level) {
        return Math.max(1, (int) Math.round(hitDie * level * statBonus(constitutionScore)));
    }

    public static int maxMana(int manaGainPerLevel, int menScore, int level) {
        return Math.max(0, (int) Math.round(manaGainPerLevel * level * statBonus(menScore)));
    }

    public static int healthRegenPerTick(int maxHealth, int constitutionScore) {
        return Math.max(1, (int) Math.round(maxHealth * HP_REGEN_RATE * statBonus(constitutionScore)));
    }

    public static int manaRegenPerTick(int maxMana, int menScore) {
        return Math.max(1, (int) Math.round(maxMana * MP_REGEN_RATE * statBonus(menScore)));
    }

    public static int accuracy(int level, int dexterityScore, int accuracyItemBonus) {
        int raw = (int) Math
                .round(BASE_ACCURACY + level + statBonus(dexterityScore) * ACCURACY_FACTOR + accuracyItemBonus);
        return Math.max(1, raw);
    }

    public static int evasion(int level, int dexterityScore, int armorWeightPenalty, int evasionItemBonus) {
        int raw = (int) Math.round(BASE_EVASION + level + statBonus(dexterityScore) * EVASION_FACTOR
                + armorWeightPenalty + evasionItemBonus);
        return Math.max(0, raw);
    }

    public static int criticalRate(int dexterityScore, int critItemBonus) {
        int raw = (int) Math.round(BASE_CRIT_RATE + statBonus(dexterityScore) * ACCURACY_FACTOR + critItemBonus);
        return Math.min(MAX_CRIT_RATE, Math.max(MIN_CRIT_RATE, raw));
    }

    // Miroir magique de criticalRate() : DEX pilote le critique physique, WIT le
    // critique magique.
    public static int magicCriticalRate(int witScore, int critItemBonus) {
        int raw = (int) Math.round(BASE_CRIT_RATE + statBonus(witScore) * ACCURACY_FACTOR + critItemBonus);
        return Math.min(MAX_CRIT_RATE, Math.max(MIN_CRIT_RATE, raw));
    }

    public static double hitChance(int accuracy, int evasion) {
        double chance = (double) accuracy / (accuracy + evasion);
        return Math.min(MAX_HIT_CHANCE, Math.max(MIN_HIT_CHANCE, chance));
    }

    public static int resolveDamage(int attackerAtk, int defenderDef, double variance, boolean critical) {
        double base = attackerAtk * ((double) attackerAtk / (attackerAtk + defenderDef)) * variance;
        if (critical) {
            base *= CRITICAL_MULTIPLIER;
        }
        return Math.max(1, (int) Math.round(base));
    }

    // Chance (0.0-1.0) qu'un debuff soit résisté, indépendamment du jet de
    // touche du sort — MEN protège des altérations d'état comme CON protège des
    // dégâts physiques.
    public static double debuffResistChance(int menScore) {
        int raw = (int) Math.round(BASE_DEBUFF_RESIST + statBonus(menScore) * DEBUFF_RESIST_FACTOR);
        return Math.min(MAX_DEBUFF_RESIST, Math.max(0, raw)) / 100.0;
    }

    // N'applique le bonus d'enchant que si l'item porte déjà ce stat (baseStat >
    // 0) : évite qu'une armure enchantée gagne un p.atk fantôme, et vice versa.
    public static int enchantBonus(int baseStat, int enchantLevel, int bonusPerLevel) {
        return baseStat > 0 ? baseStat + enchantLevel * bonusPerLevel : baseStat;
    }

    // Un score positif (résistance) réduit les dégâts jusqu'à x0.1, un score
    // négatif (vulnérabilité) les amplifie jusqu'à x2 — jamais en dessous de 1.
    public static int applyElementalResistance(int rawDamage, int resistScore) {
        double multiplier = 1.0 - resistScore * ELEMENT_RESIST_FACTOR;
        multiplier = Math.min(MAX_ELEMENT_MULTIPLIER, Math.max(MIN_ELEMENT_MULTIPLIER, multiplier));
        return Math.max(1, (int) Math.round(rawDamage * multiplier));
    }

    // atk.spd suit DEX comme p.crit/accuracy (même statBonus), pondéré par la
    // vitesse d'attaque naturelle de l'arme (0 → dégénère en pur DEX, une arme
    // "lourde" ayant un atkSpd de base plus faible qu'une dague).
    public static int attackSpeed(int weaponAtkSpd, int dexterityScore) {
        return Math.max(1, (int) Math.round(weaponAtkSpd * statBonus(dexterityScore)));
    }

    // Formule L2 canonique : délai entre deux coups (ms) = 500 000 / atk.spd —
    // atk.spd=300 (base, à neutre) donne ~1666ms, plus l'atk.spd est haut, plus
    // l'attaque est rapide.
    public static Duration attackCooldown(int atkSpd) {
        return Duration.ofMillis(Math.round(ATK_SPD_DELAY_CONSTANT / Math.max(1, atkSpd)));
    }

    public static int armorWeightPenalty(ArmorCategory category) {
        if (category == null) {
            return 0;
        }
        return switch (category) {
            case LIGHT -> 0;
            case MEDIUM -> -4;
            case HEAVY -> -10;
        };
    }
}
