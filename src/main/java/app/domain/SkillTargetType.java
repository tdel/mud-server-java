package app.domain;

// ONE : la cible sélectionnée ; AOE : la cible et les ennemis autour d'elle ;
// AURA : les ennemis autour du lanceur ; FRONT : les ennemis devant le lanceur
// (Wild Sweep) ; SELF : toujours le lanceur, quelle que soit la sélection
// (Self Heal, Relax) ; PARTY : le lanceur et les membres de son groupe présents
// autour de lui (Group Heal) — le lanceur seul s'il n'a pas de groupe ;
// SERVITOR : le serviteur invoqué du lanceur ; CORPSE : le cadavre de monstre
// le plus proche à portée ; DEAD_PLAYER : le joueur mort sélectionné ;
// DEAD_PARTY : les membres morts du groupe autour du lanceur.
public enum SkillTargetType {
    ONE, AOE, AURA, FRONT, SELF, PARTY, SERVITOR, CORPSE, DEAD_PLAYER, DEAD_PARTY;

    // Cibles déterminées par le serveur sans sélection préalable.
    public boolean selfCentered() {
        return this == SELF || this == PARTY || this == AURA || this == FRONT || this == SERVITOR || this == CORPSE
                || this == DEAD_PARTY;
    }
}
