package app.game.combat;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import app.domain.actor.Attribute;
import app.domain.actor.ModifiedStat;
import app.domain.item.ArmorCategory;
import app.game.Randomizer;

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
 * {@code app.game.Randomizer}.
 *
 * <p>
 * Constantes calibrées par simulation déterministe (espérance de dégâts, pas de
 * tirages réels) sur un Fighter/Mystic niveau 1 fraîchement créé (arme de
 * départ, sans armure) contre chaque monstre de {@code data/monsters
 * .xml} : viser un hitChance en miroir ~65-70%, une marge de temps-de-mise-
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
    // Écart accuracy/evasion volontairement faible (contrairement à un ancien
    // 45/15) : depuis le passage de hitChance() à la formule additive L2J
    // (calcHitMiss), l'écart pèse directement en points de pourcentage (facteur
    // HIT_CHANCE_FACTOR) plutôt que comme un ratio auto-limité — un écart de 30
    // saturait quasi toujours le hitChance à MAX_HIT_CHANCE, quel que soit DEX.
    public static final int BASE_ACCURACY = 30;
    public static final int BASE_EVASION = 36;
    public static final int BASE_CRIT_RATE = 8;
    public static final int MIN_CRIT_RATE = 1;
    public static final int MAX_CRIT_RATE = 90;
    public static final double MIN_HIT_CHANCE = 0.20;
    public static final double MAX_HIT_CHANCE = 0.98;
    // calcHitMiss L2J : chance = clamp(80 + 2*(accuracy-evasion), [20,98]) %.
    public static final double HIT_CHANCE_BASE = 0.80;
    public static final double HIT_CHANCE_FACTOR = 0.02;
    public static final double CRITICAL_MULTIPLIER = 2.0;
    // Valeurs L2J vérifiées (soulshot : dégâts physiques finaux x2 ; spiritshot :
    // m.atk x2 avant la racine carrée de resolveMagicalDamage/resolveHeal, ce qui
    // donne un gain réel de dégâts x√2 — même comportement que L2J puisque m.atk y
    // est aussi sous racine).
    public static final double SOULSHOT_MULTIPLIER = 2.0;
    public static final double SPIRITSHOT_MULTIPLIER = 2.0;
    // Le spiritshot réduit le temps d'incantation de 30% (la stat affichée ne
    // change pas, seul le cast en cours est raccourci) ; le soulshot n'affecte ni
    // la vitesse d'attaque, ni le temps d'incantation, ni le reuse.
    public static final double SPIRITSHOT_CASTING_TIME_REDUCTION = 0.30;
    // calcMagicDam L2J : un coup critique magique multiplie les dégâts par 4, pas
    // par 2 comme au physique.
    public static final double MAGIC_CRITICAL_MULTIPLIER = 4.0;
    // Constantes de ratio de calcPhysDam/calcMagicDam (L2J) : dégâts physiques =
    // 70*atk/def, dégâts magiques = 91*sqrt(mAtk)/mDef*power(du sort).
    public static final double PHYSICAL_DAMAGE_CONSTANT = 70.0;
    public static final double MAGICAL_DAMAGE_CONSTANT = 91.0;
    public static final double ELEMENT_RESIST_FACTOR = 0.01;
    public static final double MIN_ELEMENT_MULTIPLIER = 0.1;
    public static final double MAX_ELEMENT_MULTIPLIER = 2.0;
    public static final int ENCHANT_ATK_BONUS_PER_LEVEL = 2;
    public static final int ENCHANT_DEF_BONUS_PER_LEVEL = 1;
    // Multiplicateur de m.atk dans la formule de heal L2J (handler
    // net.sf.l2j...skillhandlers.Heal) une fois les spiritshots retirés : sans
    // charge (sps/bsps), ce chemin retombe systématiquement sur mAtkMul=2.
    public static final double HEAL_MATK_MULTIPLIER = 2.0;
    public static final int BASE_DEBUFF_RESIST = 5;
    public static final double DEBUFF_RESIST_FACTOR = 3.0;
    public static final int MAX_DEBUFF_RESIST = 70;
    // Fraction du pool max régénérée par tick de RegenHealthEngine/RegenManaEngine
    // (toutes les 3s, période retail L2J HpTask/MpTask — cf. TICK_INTERVAL_MS).
    public static final double HP_REGEN_RATE = 0.02;
    public static final double MP_REGEN_RATE = 0.02;
    public static final int BASE_ATK_SPD = 300;
    public static final double ATK_SPD_DELAY_CONSTANT = 500_000.0;
    // 2 unités ≈ 2x largeur d'un personnage + longueur d'une épée longue (1 unité
    // = 1 case Tiled = 32px) ; couvre aussi confortablement l'adjacence en
    // diagonale (√2 ≈ 1.41), non couverte par l'ancienne valeur de 1.0.
    public static final double ATTACK_RANGE = 2.0;

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

    // Courbe quadratique en level (hpBase + hpAdd*level + hpMod*level^2),
    // reprenant telle quelle la table officielle L2 des PV de base par niveau
    // (Human Fighter/Mystic, cf. data/classes/*.xml) ; le résultat est ensuite
    // multiplié par statBonus(CON), comme pour p.def/m.def.
    public static int maxHealth(double hpBase, double hpAdd, double hpMod, int level, int constitutionScore) {
        double base = hpBase + hpAdd * level + hpMod * level * level;
        return Math.max(1, (int) Math.round(base * statBonus(constitutionScore)));
    }

    public static int maxMana(double mpBase, double mpAdd, double mpMod, int level, int menScore) {
        double base = mpBase + mpAdd * level + mpMod * level * level;
        return Math.max(0, (int) Math.round(base * statBonus(menScore)));
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
        return Math.clamp(raw, MIN_CRIT_RATE, MAX_CRIT_RATE);
    }

    // Miroir magique de criticalRate() : DEX pilote le critique physique, WIT le
    // critique magique.
    public static int magicCriticalRate(int witScore, int critItemBonus) {
        int raw = (int) Math.round(BASE_CRIT_RATE + statBonus(witScore) * ACCURACY_FACTOR + critItemBonus);
        return Math.clamp(raw, MIN_CRIT_RATE, MAX_CRIT_RATE);
    }

    // calcHitMiss L2J, débarrassée des bonus de hauteur/nuit/position (pas d'axe
    // Z, pas de cycle jour-nuit, pas de gestion de dos/face dans ce projet).
    public static double hitChance(int accuracy, int evasion) {
        double chance = HIT_CHANCE_BASE + HIT_CHANCE_FACTOR * (accuracy - evasion);
        return Math.clamp(chance, MIN_HIT_CHANCE, MAX_HIT_CHANCE);
    }

    // calcPhysDam L2J, débarrassée des soulshots, du blocage au bouclier, des
    // vulnérabilités par type d'arme/race de monstre et du bonus PvP (aucun de
    // ces mécanismes n'existe dans ce projet) : attackerAtk inclut déjà le
    // power(level) du sort le cas échéant (cf. SkillSystem.rollDamage).
    public static int resolvePhysicalDamage(int attackerAtk, int defenderDef, boolean critical, boolean shotCharged) {
        double damage = PHYSICAL_DAMAGE_CONSTANT * attackerAtk / defenderDef * Randomizer.randomVariance(0.9, 1.1);
        if (critical) {
            damage *= CRITICAL_MULTIPLIER;
        }
        if (shotCharged) {
            damage *= SOULSHOT_MULTIPLIER;
        }
        return Math.max(1, (int) Math.round(damage));
    }

    // calcMagicDam L2J, débarrassée des soulshots/blessed-spiritshots, du
    // blocage au bouclier, du système de résistance/échec magique
    // (ALT_GAME_MAGICFAILURES) et du bonus PvP : le power(level) du sort est un
    // facteur multiplicatif du ratio sqrt(m.atk)/m.def (pas additif comme au
    // physique), et un coup critique magique multiplie par 4 au lieu de 2. Pas
    // de variance aléatoire : l'original n'en applique pas au magique.
    public static int resolveMagicalDamage(int magicalAttack, int defenderDef, int skillPower, boolean critical,
            boolean shotCharged) {
        double effectiveMagicalAttack = shotCharged ? magicalAttack * SPIRITSHOT_MULTIPLIER : magicalAttack;
        double damage = MAGICAL_DAMAGE_CONSTANT * Math.sqrt(effectiveMagicalAttack) / defenderDef * skillPower;
        if (critical) {
            damage *= MAGIC_CRITICAL_MULTIPLIER;
        }
        return Math.max(1, (int) Math.round(damage));
    }

    // Formule L2J (handler skillhandlers.Heal) débarrassée des spiritshots/
    // blessed-spiritshots, de HEAL_PROFICIENCY/HEAL_EFFECTIVNESS (stats non
    // implémentées ici) et des variantes HEAL_STATIC/HEAL_PERCENT (un seul type
    // HEALING côté projet) : il reste power(level) + sqrt(mAtkMul * m.atk), le
    // reliquat de la branche "sans charge" de l'original.
    public static int resolveHeal(int power, int magicalAttack, boolean shotCharged) {
        double effectiveMagicalAttack = shotCharged ? magicalAttack * SPIRITSHOT_MULTIPLIER : magicalAttack;
        double amount = power + Math.sqrt(HEAL_MATK_MULTIPLIER * effectiveMagicalAttack);
        return Math.max(0, (int) Math.round(amount));
    }

    // Chance (0.0-1.0) qu'un debuff soit résisté, indépendamment du jet de
    // touche du sort — MEN protège des altérations d'état comme CON protège des
    // dégâts physiques.
    public static double debuffResistChance(int menScore) {
        int raw = (int) Math.round(BASE_DEBUFF_RESIST + statBonus(menScore) * DEBUFF_RESIST_FACTOR);
        return Math.clamp(raw, 0, MAX_DEBUFF_RESIST) / 100.0;
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
        multiplier = Math.clamp(multiplier, MIN_ELEMENT_MULTIPLIER, MAX_ELEMENT_MULTIPLIER);
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

    // Assemble en une seule map les 9 stats de combat dérivées (p.atk, m.atk,
    // p.def, m.def, accuracy, evasion, p.crit, m.crit, atk.spd) à partir des
    // composantes brutes (arme/armure ou équivalent monstre) et des attributs —
    // consommé par StatSystem, aussi bien pour un CharacterInstance (équipement
    // réel) qu'un MonsterInstance (stats naturelles issues de son template).
    public static Map<ModifiedStat, Integer> baseStats(int weaponPAtk, int weaponMAtk, int armorPDefSum,
            int armorMDefSum, int accuracyItemBonus, int evasionItemBonus, int critItemBonus, int armorWeightPenalty,
            int weaponAtkSpd, Map<Attribute, Integer> attributes, int level) {
        Map<ModifiedStat, Integer> stats = new EnumMap<>(ModifiedStat.class);
        stats.put(ModifiedStat.PATK, physicalAttack(weaponPAtk, attributes.get(Attribute.STR), level));
        stats.put(ModifiedStat.MATK, magicalAttack(weaponMAtk, attributes.get(Attribute.INT), level));
        stats.put(ModifiedStat.PDEF, physicalDefense(armorPDefSum, attributes.get(Attribute.CON)));
        stats.put(ModifiedStat.MDEF, magicalDefense(armorMDefSum, attributes.get(Attribute.MEN)));
        stats.put(ModifiedStat.ACCURACY, accuracy(level, attributes.get(Attribute.DEX), accuracyItemBonus));
        stats.put(ModifiedStat.EVASION,
                evasion(level, attributes.get(Attribute.DEX), armorWeightPenalty, evasionItemBonus));
        stats.put(ModifiedStat.PCRIT, criticalRate(attributes.get(Attribute.DEX), critItemBonus));
        stats.put(ModifiedStat.MCRIT, magicCriticalRate(attributes.get(Attribute.WIT), critItemBonus));
        stats.put(ModifiedStat.ATKSPD, attackSpeed(weaponAtkSpd, attributes.get(Attribute.DEX)));
        return stats;
    }
}
