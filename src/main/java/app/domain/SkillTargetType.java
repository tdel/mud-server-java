package app.domain;

// ONE : la cible sélectionnée ; AOE : tous les occupants autour de la cible ;
// SELF : toujours le lanceur, quelle que soit la sélection (Self Heal, Relax) ;
// PARTY : le lanceur et les membres de son groupe présents autour de lui
// (Group Heal) — le lanceur seul s'il n'a pas de groupe.
public enum SkillTargetType {
    ONE, AOE, SELF, PARTY
}
