package app.domain.item;

// Types d'armure de Lineage 2 (L2J ArmorType HEAVY/LIGHT/MAGIC) : armure lourde
// (plaques, mailles), armure légère (cuir) et robe (tenue des mystiques). Porté
// par le plastron (slot CHEST) : pénalité d'esquive (CombatFormulas
// .armorWeightPenalty) et conditions des passifs (Spellcraft, Mana Recovery,
// Armor Mastery, cf. SkillSystem.passiveAdditive).
public enum ArmorCategory {
    HEAVY, LIGHT, ROBE
}
