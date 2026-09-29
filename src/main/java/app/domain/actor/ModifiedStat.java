package app.domain.actor;

public enum ModifiedStat {
    // HP_REGEN/MP_REGEN : jamais dans les stats de base (0) — seuls des effets et
    // des passifs les modifient, appliqués au montant de régénération par tick
    // (cf. ResourceSystem.healthRegenAmountPerTick / StatSystem.adjust).
    ACCURACY, EVASION, PATK, PDEF, MATK, MDEF, PCRIT, MCRIT, ATKSPD, CASTSPD, SPEED, HP_REGEN, MP_REGEN;

    public String label() {
        return switch (this) {
            case ACCURACY -> "accuracy";
            case EVASION -> "evasion";
            case PATK -> "P.Atk.";
            case PDEF -> "P.Def.";
            case MATK -> "M.Atk.";
            case MDEF -> "M.Def.";
            case PCRIT -> "P.Crit.";
            case MCRIT -> "M.Crit.";
            case ATKSPD -> "Atk.Spd.";
            case CASTSPD -> "Casting Spd.";
            case SPEED -> "Speed";
            case HP_REGEN -> "HP Regen.";
            case MP_REGEN -> "MP Regen.";
        };
    }
}
