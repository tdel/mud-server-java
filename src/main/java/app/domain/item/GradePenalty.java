package app.domain.item;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import app.domain.ActiveEffect;
import app.domain.StatModifier;
import app.domain.StatOperator;
import app.domain.actor.ModifiedStat;

// Pénalités de grade de Lineage 2 High Five (skills 6209 Weapon Grade Penalty et
// 6213 Armor Grade Penalty du datapack L2J) : porter un objet d'un grade
// au-delà de l'expertise débloquée (Expertise Grade) pose un debuff permanent
// tant que l'objet reste équipé. Arme et armure (bijoux et bouclier compris)
// sont deux pénalités distinctes ; le niveau de chacune est l'écart de grade le
// plus grand parmi les objets concernés, plafonné à MAX_LEVEL (cf.
// PcInstance.refreshExpertisePenalty de L2J). Pas de dégâts critiques dans nos
// stats : le critDmg de 6209 n'a pas d'équivalent.
public final class GradePenalty {

    public static final UUID WEAPON_EFFECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID ARMOR_EFFECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    public static final String WEAPON_EFFECT_NAME = "Weapon Grade Penalty";
    public static final String ARMOR_EFFECT_NAME = "Armor Grade Penalty";
    public static final int MAX_LEVEL = 4;

    // 6209 : accCombat -16, pAtk ×0.9, pAtkSpd ×0.9, critRate ×0.9/0.8/0.7/0.6.
    private static final int WEAPON_ACCURACY = -16;
    private static final int WEAPON_PATK_PERCENT = -10;
    private static final int WEAPON_ATKSPD_PERCENT = -10;
    private static final int[] WEAPON_CRIT_PERCENT = {-10, -20, -30, -40};
    // 6213 : rEvas -2.5/-5/-7.5/-10 (arrondi à l'entier), pAtkSpd/mAtkSpd/runSpd
    // ×0.8333/0.6944/0.5787/0.4823 (soit -17/-31/-42/-52 %).
    private static final int[] ARMOR_EVASION = {-3, -5, -8, -10};
    private static final int[] ARMOR_SPEED_PERCENT = {-17, -31, -42, -52};

    private GradePenalty() {
    }

    public static boolean isGradePenalty(UUID effectId) {
        return WEAPON_EFFECT_ID.equals(effectId) || ARMOR_EFFECT_ID.equals(effectId);
    }

    // Niveau de pénalité d'un objet de grade `itemGrade` porté avec l'expertise
    // `expertise` : 0 si l'expertise suffit.
    public static int levelFor(ItemGrade itemGrade, ItemGrade expertise) {
        return Math.clamp(itemGrade.ordinal() - expertise.ordinal(), 0, MAX_LEVEL);
    }

    public static Optional<ActiveEffect> weapon(int level) {
        if (level <= 0) {
            return Optional.empty();
        }
        int index = Math.min(level, MAX_LEVEL) - 1;
        return Optional.of(permanent(WEAPON_EFFECT_ID, WEAPON_EFFECT_NAME, List.of(
                new StatModifier(ModifiedStat.ACCURACY, WEAPON_ACCURACY, StatOperator.ADDITIVE),
                new StatModifier(ModifiedStat.PATK, WEAPON_PATK_PERCENT, StatOperator.MULTIPLICATIVE),
                new StatModifier(ModifiedStat.ATKSPD, WEAPON_ATKSPD_PERCENT, StatOperator.MULTIPLICATIVE),
                new StatModifier(ModifiedStat.PCRIT, WEAPON_CRIT_PERCENT[index], StatOperator.MULTIPLICATIVE))));
    }

    public static Optional<ActiveEffect> armor(int level) {
        if (level <= 0) {
            return Optional.empty();
        }
        int index = Math.min(level, MAX_LEVEL) - 1;
        int speed = ARMOR_SPEED_PERCENT[index];
        return Optional.of(permanent(ARMOR_EFFECT_ID, ARMOR_EFFECT_NAME,
                List.of(new StatModifier(ModifiedStat.EVASION, ARMOR_EVASION[index], StatOperator.ADDITIVE),
                        new StatModifier(ModifiedStat.ATKSPD, speed, StatOperator.MULTIPLICATIVE),
                        new StatModifier(ModifiedStat.CASTSPD, speed, StatOperator.MULTIPLICATIVE),
                        new StatModifier(ModifiedStat.SPEED, speed, StatOperator.MULTIPLICATIVE))));
    }

    private static ActiveEffect permanent(UUID id, String name, List<StatModifier> modifiers) {
        return new ActiveEffect(id, name, modifiers, ActiveEffect.PERMANENT);
    }
}
