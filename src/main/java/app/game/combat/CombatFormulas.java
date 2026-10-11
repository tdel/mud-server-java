package app.game.combat;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import app.domain.actor.Attribute;
import app.domain.actor.ModifiedStat;
import app.domain.item.ItemGrade;
import app.domain.item.WeaponType;
import app.game.Randomizer;

/**
 * Formules de combat de Lineage 2 High Five : stats dérivées (p.atk, m.atk,
 * p.def, m.def, précision, esquive, critiques, vitesses, PV/PM, régénération)
 * et résolution d'un coup (touche, critique, dégâts). Portées des fonctions de
 * stats du serveur L2JMobius H5 ({@code model/stats/functions/formulas/Func*},
 * {@code Formulas}) appliquées aux valeurs de base du datapack (classes
 * {@code stats/chars/baseStats}, objets {@code stats/items}, PNJ
 * {@code stats/npcs}) : un joueur, un monstre ou un serviteur affiche ici les
 * mêmes valeurs qu'en jeu L2.
 *
 * <p>
 * Fonctions pures, sans dépendance Spring ni RNG : le tirage aléatoire réel
 * (hit/critique/variance) reste à la charge de l'appelant, via
 * {@code app.game.Randomizer}.
 */
public final class CombatFormulas {

    // Plafonds L2J (Config.MAX_PCRIT_RATE / MAX_MCRIT_RATE) des taux critiques,
    // exprimés comme en L2 en millièmes (88 = 8,8 % de chance).
    public static final int MAX_CRIT_RATE = 500;
    public static final int MAX_MAGIC_CRIT_RATE = 200;
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
    // Multiplicateur de m.atk dans la formule de heal L2J (handler
    // net.sf.l2j...skillhandlers.Heal) une fois les spiritshots retirés : sans
    // charge (sps/bsps), ce chemin retombe systématiquement sur mAtkMul=2.
    public static final double HEAL_MATK_MULTIPLIER = 2.0;
    public static final int BASE_DEBUFF_RESIST = 5;
    public static final double DEBUFF_RESIST_FACTOR = 3.0;
    public static final int MAX_DEBUFF_RESIST = 70;
    // Régénération L2 selon l'activité (Formulas.calcHpRegen/calcMpRegen) : assis
    // (Relax) x1.5, immobile x1.1, en course x0.7.
    public static final double REGEN_RESTING_FACTOR = 1.5;
    public static final double REGEN_STANDING_FACTOR = 1.1;
    public static final double REGEN_MOVING_FACTOR = 0.7;
    public static final double ATK_SPD_DELAY_CONSTANT = 500_000.0;
    // Vitesse d'incantation de base de toute créature L2 (baseMAtkSpd 333).
    public static final int BASE_CAST_SPD = 333;
    // Critique magique de base (MCRITICAL_RATE initial de
    // CharStat.getMCriticalHit).
    public static final double BASE_MAGIC_CRIT = 1.0;
    // 2 unités ≈ 2x largeur d'un personnage + longueur d'une épée longue (1 unité
    // = 1 case Tiled = 32px) ; couvre aussi confortablement l'adjacence en
    // diagonale (√2 ≈ 1.41), non couverte par l'ancienne valeur de 1.0.
    public static final double ATTACK_RANGE = 2.0;

    private CombatFormulas() {
    }

    // Modificateur de niveau L2 (L2Character.getLevelMod) : 0.90 au niveau 1,
    // 1.29 au niveau 40.
    public static double levelMod(int level) {
        return (level + 89) / 100.0;
    }

    // FuncPAtkMod : p.atk de base (arme, sinon poing de la classe) x STR x niveau.
    public static int physicalAttack(double basePAtk, int strengthScore, int level) {
        return (int) Math.round(basePAtk * Attribute.STR.bonus(strengthScore) * levelMod(level));
    }

    // FuncMAtkMod : m.atk de base x INT² x niveau².
    public static int magicalAttack(double baseMAtk, int intelligenceScore, int level) {
        double intBonus = Attribute.INT.bonus(intelligenceScore);
        double levelMod = levelMod(level);
        return (int) Math.round(baseMAtk * intBonus * intBonus * levelMod * levelMod);
    }

    // FuncPDefMod : somme des défenses (pièces portées, et défense "nue" de la
    // classe pour les emplacements vides) x niveau. CON n'y joue pas.
    public static int physicalDefense(double basePDef, int level) {
        return Math.max(1, (int) Math.round(basePDef * levelMod(level)));
    }

    // FuncMDefMod : somme des bijoux (ou défense nue par emplacement) x MEN x
    // niveau.
    public static int magicalDefense(double baseMDef, int menScore, int level) {
        return Math.max(1, (int) Math.round(baseMDef * Attribute.MEN.bonus(menScore) * levelMod(level)));
    }

    // FuncMaxHpMul / FuncMaxMpMul : PV/PM de base (table par niveau de la classe,
    // ou valeur du PNJ) x CON / MEN.
    public static int maxHealth(double baseHp, int constitutionScore) {
        return Math.max(1, (int) Math.round(baseHp * Attribute.CON.bonus(constitutionScore)));
    }

    public static int maxMana(double baseMp, int menScore) {
        return Math.max(0, (int) Math.round(baseMp * Attribute.MEN.bonus(menScore)));
    }

    // Formulas.calcHpRegen/calcMpRegen (joueur) : régénération de base de la
    // classe au niveau x modificateur de niveau x CON (PV) / MEN (PM) x facteur
    // d'activité, par tick de 3 s.
    public static int healthRegenPerTick(double baseRegen, int level, int constitutionScore, double activityFactor) {
        double regen = Math.max(1, baseRegen * levelMod(level) * Attribute.CON.bonus(constitutionScore));
        return Math.max(1, (int) Math.round(regen * activityFactor));
    }

    public static int manaRegenPerTick(double baseRegen, int level, int menScore, double activityFactor) {
        double regen = Math.max(1, baseRegen * levelMod(level) * Attribute.MEN.bonus(menScore));
        return Math.max(1, (int) Math.round(regen * activityFactor));
    }

    // FuncAtkAccuracy : sqrt(DEX)*6 + niveau + bonus de l'arme (accCombat, ex.
    // +4.75 masse, -3.75 dague/arc/hast), avec les paliers L2 au-delà du niveau 69.
    public static int accuracy(int level, int dexterityScore, double accuracyBonus) {
        double value = Math.sqrt(dexterityScore) * 6 + level + accuracyBonus;
        if (level > 77) {
            value += level - 76;
        }
        if (level > 69) {
            value += level - 69;
        }
        return (int) Math.round(value);
    }

    // FuncAtkEvasion : sqrt(DEX)*6 + niveau + bonus (bouclier -8, PNJ : evasion
    // du datapack).
    public static int evasion(int level, int dexterityScore, double evasionBonus, boolean player) {
        double value = Math.sqrt(dexterityScore) * 6 + level + evasionBonus;
        if (player && level >= 70) {
            value += (level - 69) * (level >= 78 ? 1.2 : 1.0);
        } else if (!player && level > 69) {
            value += level - 69 + 2;
        }
        return (int) Math.round(value);
    }

    // FuncAtkCritical : critique de base (arme : 8 épée, 12 dague/arc, 4 masse/
    // poing...) x DEX x 10, en millièmes, plafonné à MAX_CRIT_RATE.
    public static int criticalRate(double baseCritRate, int dexterityScore) {
        int rate = (int) Math.round(baseCritRate * Attribute.DEX.bonus(dexterityScore) * 10);
        return Math.min(rate, MAX_CRIT_RATE);
    }

    // CharStat.getMCriticalHit + FuncMAtkCritical : 1 x WIT x 10 (bonus de WIT
    // seulement arme en main pour un joueur, toujours pour un PNJ), tronqué puis
    // x10, en millièmes, plafonné à MAX_MAGIC_CRIT_RATE.
    public static int magicCriticalRate(int witScore, boolean witApplies) {
        double base = witApplies ? BASE_MAGIC_CRIT * Attribute.WIT.bonus(witScore) * 10 : BASE_MAGIC_CRIT;
        return Math.min((int) base * 10, MAX_MAGIC_CRIT_RATE);
    }

    // FuncPAtkSpeed : vitesse d'attaque de l'arme (379 épée/masse, 433 dague, 325
    // deux mains, 293 arc ; 300 à mains nues) x DEX.
    public static int attackSpeed(int baseAtkSpd, int dexterityScore) {
        return Math.max(1, (int) Math.round(baseAtkSpd * Attribute.DEX.bonus(dexterityScore)));
    }

    // FuncMAtkSpeed : 333 x WIT.
    public static int castSpeed(int witScore) {
        return Math.max(1, (int) Math.round(BASE_CAST_SPD * Attribute.WIT.bonus(witScore)));
    }

    // FuncMoveSpeed : vitesse de course de base (classe ou PNJ) x DEX.
    public static int runSpeed(double baseRunSpeed, int dexterityScore) {
        return Math.max(0, (int) Math.round(baseRunSpeed * Attribute.DEX.bonus(dexterityScore)));
    }

    // calcHitMiss L2J, débarrassée des bonus de hauteur/nuit/position (pas d'axe
    // Z, pas de cycle jour-nuit, pas de gestion de dos/face dans ce projet).
    public static double hitChance(int accuracy, int evasion) {
        double chance = HIT_CHANCE_BASE + HIT_CHANCE_FACTOR * (accuracy - evasion);
        return Math.clamp(chance, MIN_HIT_CHANCE, MAX_HIT_CHANCE);
    }

    // Chance (0-1) d'un taux critique L2 exprimé en millièmes.
    public static double critChance(int rate) {
        return Math.max(0, rate) / 1000.0;
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
        int raw = (int) Math.round(BASE_DEBUFF_RESIST + Attribute.MEN.bonus(menScore) * DEBUFF_RESIST_FACTOR);
        return Math.clamp(raw, 0, MAX_DEBUFF_RESIST) / 100.0;
    }

    // FuncEnchant L2J, p.atk d'une arme : bonus par niveau selon le grade et la
    // tenue (une main, deux mains, arc), doublé au-delà de +3 (sur-enchantement).
    // Rien sur un objet qui ne porte pas déjà la stat (base 0).
    public static int enchantPAtk(int base, int enchant, ItemGrade grade, WeaponType weaponType) {
        if (base <= 0 || enchant <= 0 || weaponType == null) {
            return base;
        }
        boolean bow = weaponType == WeaponType.BOW;
        boolean twoHanded = weaponType.twoHanded();
        int perLevel = switch (grade) {
            case S -> bow ? 10 : twoHanded ? 6 : 5;
            case A -> bow ? 8 : twoHanded ? 5 : 4;
            case B, C -> bow ? 6 : twoHanded ? 4 : 3;
            case D, NOGRADE -> bow ? 4 : 2;
        };
        return base + enchantSteps(enchant, perLevel);
    }

    // FuncEnchant L2J, m.atk : +2 (D/sans grade), +3 (C/B/A), +4 (S) par niveau.
    public static int enchantMAtk(int base, int enchant, ItemGrade grade) {
        if (base <= 0 || enchant <= 0) {
            return base;
        }
        int perLevel = switch (grade) {
            case S -> 4;
            case A, B, C -> 3;
            case D, NOGRADE -> 2;
        };
        return base + enchantSteps(enchant, perLevel);
    }

    // FuncEnchant L2J, p.def/m.def : +1 par niveau jusqu'à +3, +3 au-delà.
    public static int enchantDefense(int base, int enchant) {
        if (base <= 0 || enchant <= 0) {
            return base;
        }
        int safe = Math.min(enchant, 3);
        return base + safe + 3 * (enchant - safe);
    }

    private static int enchantSteps(int enchant, int perLevel) {
        int safe = Math.min(enchant, 3);
        return perLevel * safe + 2 * perLevel * (enchant - safe);
    }

    // Un score positif (résistance) réduit les dégâts jusqu'à x0.1, un score
    // négatif (vulnérabilité) les amplifie jusqu'à x2 — jamais en dessous de 1.
    public static int applyElementalResistance(int rawDamage, int resistScore) {
        double multiplier = 1.0 - resistScore * ELEMENT_RESIST_FACTOR;
        multiplier = Math.clamp(multiplier, MIN_ELEMENT_MULTIPLIER, MAX_ELEMENT_MULTIPLIER);
        return Math.max(1, (int) Math.round(rawDamage * multiplier));
    }

    // Formule L2 canonique : délai entre deux coups (ms) = 500 000 / atk.spd —
    // 379 (épée, DEX neutre) donne ~1319 ms, plus l'atk.spd est haut, plus
    // l'attaque est rapide.
    public static Duration attackCooldown(int atkSpd) {
        return Duration.ofMillis(Math.round(ATK_SPD_DELAY_CONSTANT / Math.max(1, atkSpd)));
    }

    // Formule L2J (Skill.calcHitTime côté client officiel/L2J applique le même
    // ratio) : durée réelle d'incantation = durée d'auteur du sort (calibrée pour
    // un cast.spd neutre, BASE_CAST_SPD) * BASE_CAST_SPD / cast.spd effectif.
    public static long effectiveCastingTimeMs(long authoredCastingTimeMs, int castSpd) {
        return Math.round(authoredCastingTimeMs * (double) BASE_CAST_SPD / Math.max(1, castSpd));
    }

    /**
     * Valeurs de base d'une créature avant les fonctions de stats L2 : celles de
     * l'équipement (ou de la classe à nu) pour un joueur, celles du datapack pour
     * un PNJ.
     *
     * @param pAtk
     *            p.atk de l'arme, ou du poing de la classe
     * @param mAtk
     *            m.atk de l'arme, ou de la classe
     * @param pDef
     *            somme des p.def portées et des défenses nues restantes
     * @param mDef
     *            somme des m.def portées et des défenses nues restantes
     * @param accuracyBonus
     *            accCombat (arme, ou accuracy du PNJ)
     * @param evasionBonus
     *            bonus/malus d'esquive (bouclier, evasion du PNJ)
     * @param critRate
     *            critique de base (arme, ou classe/PNJ)
     * @param critBonus
     *            bonus de critique plat ajouté après DEX (millièmes)
     * @param atkSpd
     *            vitesse d'attaque de base
     * @param runSpeed
     *            vitesse de course de base
     * @param magicCritFromWit
     *            WIT augmente le critique magique (PNJ, joueur arme en main)
     * @param player
     *            esquive façon joueur (paliers au-delà du niveau 69)
     */
    public record BaseValues(double pAtk, double mAtk, double pDef, double mDef, double accuracyBonus,
            double evasionBonus, double critRate, int critBonus, int atkSpd, double runSpeed, boolean magicCritFromWit,
            boolean player) {
    }

    // Applique les fonctions de stats L2 (Func*) aux valeurs de base : p.atk,
    // m.atk, p.def, m.def, précision, esquive, critiques, vitesses d'attaque,
    // d'incantation et de course — consommé par StatSystem, pour un joueur
    // (InventorySystem.computeBaseStats), un monstre (MonsterCatalog) ou un
    // serviteur (SummonInstance).
    public static Map<ModifiedStat, Integer> stats(BaseValues base, Map<Attribute, Integer> attributes, int level) {
        Map<ModifiedStat, Integer> stats = new EnumMap<>(ModifiedStat.class);
        int dex = attributes.get(Attribute.DEX);
        stats.put(ModifiedStat.PATK, physicalAttack(base.pAtk(), attributes.get(Attribute.STR), level));
        stats.put(ModifiedStat.MATK, magicalAttack(base.mAtk(), attributes.get(Attribute.INT), level));
        stats.put(ModifiedStat.PDEF, physicalDefense(base.pDef(), level));
        stats.put(ModifiedStat.MDEF, magicalDefense(base.mDef(), attributes.get(Attribute.MEN), level));
        stats.put(ModifiedStat.ACCURACY, accuracy(level, dex, base.accuracyBonus()));
        stats.put(ModifiedStat.EVASION, evasion(level, dex, base.evasionBonus(), base.player()));
        stats.put(ModifiedStat.PCRIT, Math.min(criticalRate(base.critRate(), dex) + base.critBonus(), MAX_CRIT_RATE));
        stats.put(ModifiedStat.MCRIT, magicCriticalRate(attributes.get(Attribute.WIT), base.magicCritFromWit()));
        stats.put(ModifiedStat.ATKSPD, attackSpeed(base.atkSpd(), dex));
        stats.put(ModifiedStat.CASTSPD, castSpeed(attributes.get(Attribute.WIT)));
        stats.put(ModifiedStat.SPEED, runSpeed(base.runSpeed(), dex));
        return stats;
    }
}
