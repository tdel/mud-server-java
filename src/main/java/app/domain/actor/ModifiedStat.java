package app.domain.actor;

public enum ModifiedStat {
    ACCURACY, EVASION, PATK, PDEF, MATK, MDEF;

    public String label() {
        return switch (this) {
            case ACCURACY -> "accuracy";
            case EVASION -> "evasion";
            case PATK -> "P.Atk.";
            case PDEF -> "P.Def.";
            case MATK -> "M.Atk.";
            case MDEF -> "M.Def.";
        };
    }
}
