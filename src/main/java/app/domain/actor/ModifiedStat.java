package app.domain.actor;

public enum ModifiedStat {
    ACCURACY, EVASION, PATK, PDEF, MATK, MDEF, PCRIT, MCRIT, ATKSPD;

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
        };
    }
}
