package app.domain.actor;

// Race L2 d'un monstre ou d'un serviteur (npc race du datapack L2J) : cible des
// compétences de faiblesse (Detect Animal/Insect Weakness, DMG_VS_*) et des
// sorts réservés aux morts-vivants (Disrupt Undead, Requiem).
public enum CreatureRace {
    ANIMAL, BEAST, BUG, UNDEAD, HUMANOID, FAIRY, ELEMENTAL, PLANT, DEMONIC, GIANT, CONSTRUCT
}
