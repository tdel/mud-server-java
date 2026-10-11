package app.domain;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import app.domain.actor.Attribute;
import app.domain.item.WeaponType;

// requiredWeapons : familles d'arme exigées pour lancer le sort (vide = aucune
// contrainte), ex: Power Strike (épée/masse), Mortal Blow (dague), Power Shot
// (arc) ; shieldRequired : bouclier exigé (Shield Stun). blowChance : chance de
// base (%) d'un coup "blow" (Mortal Blow) — raté sinon, 0 pour un sort
// ordinaire ; behindOnly : seulement dans le dos de la cible (Backstab) ;
// halfLethal : chance (%) de réduire la cible à la moitié de ses PV (Deadly
// Blow). drainPercent : part des dégâts rendue en PV au lanceur (Vampiric
// Touch). landRate : chance de base (%) qu'un debuff se pose (0 = règle
// historique : jet de touche seul), réduite par resistAttribute de la cible
// (MEN, CON pour le poison). breakOnAction : l'effet posé cesse au premier
// mouvement/attaque/sort du porteur (Relax). targetRace : race de monstre
// exigée (Disrupt Undead), null sinon. summonId / cubic / cureKind : paramètres
// des types SUMMON / CUBIC / CURE.
public record ActiveSkill(UUID id, String name, String description, List<SkillLevel> levels, int reuseTimeMs,
        int castingTimeMs, int range, int aoeRadius, SkillEffectType skillType, SkillTargetType target,
        SkillElement element, SkillDamageType damageType, boolean projectile, int projectileSpeed,
        List<SkillEffectDefinition> effects, Set<WeaponType> requiredWeapons, int blowChance, int drainPercent,
        int landRate, Attribute resistAttribute, boolean breakOnAction, boolean shieldRequired, boolean behindOnly,
        int halfLethal, String targetRace, String summonId, CubicType cubic, CureKind cureKind) {

    public int maxLevel() {
        return levels.size();
    }

    public SkillLevel levelAt(int level) {
        return levels.get(level - 1);
    }

    public int powerAt(int level) {
        return levels.get(level - 1).power();
    }

    public int manaCostAt(int level) {
        return levels.get(level - 1).mana();
    }

    public int hpCostAt(int level) {
        return levels.get(level - 1).hpCost();
    }

    public boolean harmful() {
        return skillType == SkillEffectType.DAMAGE || skillType == SkillEffectType.DEBUFF
                || skillType == SkillEffectType.AGGRO || skillType == SkillEffectType.PEACE;
    }

    // Sort magique au sens L2 (bloqué par Silence, accéléré par les
    // spiritshots) : tout ce qui n'est pas une technique physique.
    public boolean magic() {
        return damageType == SkillDamageType.MAGICAL;
    }
}
