package app.domain;

import app.domain.actor.ModifiedStat;

// Altérations d'état L2 portées par un effet (ActiveEffect.status) :
// STUN/SLEEP/PARALYZE empêchent tout (déplacement, attaque, sort) — SLEEP cesse
// au premier dégât ; ROOT/IMMOBILE (Dryad Root, Ultimate Defense) empêchent
// seulement de bouger ; FEAR fait fuir ; SILENCE interdit les sorts magiques ;
// CONFUSION fait attaquer au hasard ; PASSIVE (Requiem) fait cesser le combat à
// un monstre ; FAKE_DEATH/SILENT_MOVE (toggles du Treasure Hunter) rendent le
// porteur invisible à l'agressivité des monstres. resistStat : résistance de
// trait L2 (Lionheart, Mental Shield...) qui réduit la chance de se poser.
public enum AbnormalStatus {
    STUN(ModifiedStat.RESIST_STUN), SLEEP(ModifiedStat.RESIST_SLEEP), PARALYZE(ModifiedStat.RESIST_HOLD), ROOT(
            ModifiedStat.RESIST_HOLD), IMMOBILE(null), FEAR(ModifiedStat.RESIST_DERANGEMENT), SILENCE(
                    ModifiedStat.RESIST_DERANGEMENT), CONFUSION(ModifiedStat.RESIST_DERANGEMENT), PASSIVE(
                            ModifiedStat.RESIST_DERANGEMENT), FAKE_DEATH(null), SILENT_MOVE(null);

    private final ModifiedStat resistStat;

    AbnormalStatus(ModifiedStat resistStat) {
        this.resistStat = resistStat;
    }

    public ModifiedStat resistStat() {
        return resistStat;
    }

    // Plus aucune action volontaire (ni pas, ni coup, ni sort).
    public boolean incapacitates() {
        return this == STUN || this == SLEEP || this == PARALYZE || this == FEAR || this == FAKE_DEATH;
    }

    public boolean blocksMovement() {
        return incapacitates() || this == ROOT || this == IMMOBILE;
    }
}
