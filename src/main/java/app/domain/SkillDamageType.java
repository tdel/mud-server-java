package app.domain;

// Détermine quelles stats un ActiveSkill de type DAMAGE utilise pour résoudre
// ses dégâts (cf. SkillSystem.rollDamage) : PHYSICAL calque l'attaque de
// mêlée normale (P.ATK/P.DEF/P.CRIT, ex: Power Strike), MAGICAL est le
// comportement historique (M.ATK/M.DEF/M.CRIT, ex: Wind Strike).
public enum SkillDamageType {
    PHYSICAL, MAGICAL
}
