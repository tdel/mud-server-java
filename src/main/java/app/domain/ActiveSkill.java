package app.domain;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import app.domain.actor.Attribute;
import app.domain.item.WeaponType;

// requiredWeapons : familles d'arme exigées pour lancer le sort (vide = aucune
// contrainte), ex: Power Strike (épée/masse), Mortal Blow (dague), Power Shot
// (arc). blowChance : chance de base (%) d'un coup "blow" (Mortal Blow) —
// raté sinon, 0 pour un sort ordinaire. drainPercent : part des dégâts rendue
// en PV au lanceur (Vampiric Touch). landRate : chance de base (%) qu'un
// debuff se pose (0 = règle historique : jet de touche seul), réduite par
// resistAttribute de la cible (MEN, CON pour le poison). breakOnAction :
// l'effet posé cesse au premier mouvement/attaque/sort du porteur (Relax).
public record ActiveSkill(UUID id, String name, String description, List<SkillLevel> levels, int reuseTimeMs,
        int castingTimeMs, int range, int aoeRadius, SkillEffectType skillType, SkillTargetType target,
        SkillElement element, SkillDamageType damageType, boolean projectile, int projectileSpeed,
        List<SkillEffectDefinition> effects, Set<WeaponType> requiredWeapons, int blowChance, int drainPercent,
        int landRate, Attribute resistAttribute, boolean breakOnAction) {

    public int maxLevel() {
        return levels.size();
    }

    public int powerAt(int level) {
        return levels.get(level - 1).power();
    }

    public int manaCostAt(int level) {
        return levels.get(level - 1).mana();
    }

    public boolean harmful() {
        return skillType == SkillEffectType.DAMAGE || skillType == SkillEffectType.DEBUFF;
    }
}
