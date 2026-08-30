package app.game.combat;

import app.domain.item.ArmorCategory;

/**
 * Formules de combat façon Lineage2 : stats dérivées (p.atk/p.def/m.atk/m.def
 * /accuracy/evasion/criticalRate) et résolution d'un coup (touche, critique,
 * dégâts). Reconstruites à partir des mécaniques largement documentées par la
 * communauté (notamment le moteur open-source L2J) — les constantes ci-dessous
 * sont un point de départ à équilibrer en jeu, pas une reproduction garantie
 * du client retail.
 *
 * <p>Fonctions pures, sans dépendance Spring ni RNG : le tirage aléatoire réel
 * (hit/critique/variance) reste à la charge de l'appelant, via
 * {@code app.game.dice.DiceRoller}.
 *
 * <p>Constantes calibrées par simulation déterministe (espérance de dégâts,
 * pas de tirages réels) sur un Fighter/Mystic niveau 1 fraîchement créé
 * (arme de départ, sans armure) contre chaque monstre de {@code data/monsters
 * .json} : viser un hitChance en miroir ~65-70%, une marge de temps-de-mise-
 * à-mort favorable au joueur sur les monstres "starter" (même niveau, faible
 * récompense XP), et une difficulté réelle sur les monstres au-dessus du
 * niveau du joueur. Point de départ à retester après tout changement de
 * contenu (nouvelles armes/armures, nouveaux monstres).
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

    public static int accuracy(int level, int dexterityScore, int accuracyItemBonus) {
        int raw = (int) Math
                .round(BASE_ACCURACY + level + statBonus(dexterityScore) * ACCURACY_FACTOR + accuracyItemBonus);
        return Math.max(1, raw);
    }

    public static int evasion(int level, int dexterityScore, int armorWeightPenalty, int evasionItemBonus) {
        int raw = (int) Math.round(
                BASE_EVASION + level + statBonus(dexterityScore) * EVASION_FACTOR + armorWeightPenalty
                        + evasionItemBonus);
        return Math.max(0, raw);
    }

    public static int criticalRate(int dexterityScore, int critItemBonus) {
        int raw = (int) Math.round(BASE_CRIT_RATE + statBonus(dexterityScore) * ACCURACY_FACTOR + critItemBonus);
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
