package fr.idev.mudserver.domain.actor;

public final class ActionEconomy {

    public static final int DEFAULT_ACTIONS_MAX = 1;
    public static final int DEFAULT_EXTRA_ACTIONS_MAX = 0;

    private int actionsMax = DEFAULT_ACTIONS_MAX;
    private int extraActionsMax = DEFAULT_EXTRA_ACTIONS_MAX;
    private int actionsRemaining = DEFAULT_ACTIONS_MAX;
    private int extraActionsRemaining = DEFAULT_EXTRA_ACTIONS_MAX;

    public int getActionsMax() {
        return actionsMax;
    }

    public int getExtraActionsMax() {
        return extraActionsMax;
    }

    public int getActionsRemaining() {
        return actionsRemaining;
    }

    public int getExtraActionsRemaining() {
        return extraActionsRemaining;
    }

    public void setActionsMax(int actionsMax) {
        this.actionsMax = actionsMax;
    }

    public void setExtraActionsMax(int extraActionsMax) {
        this.extraActionsMax = extraActionsMax;
    }

    public void grantBonusActionThisTurn() {
        actionsRemaining++;
    }

    public void grantBonusExtraActionsThisTurn(int amount) {
        extraActionsRemaining += amount;
    }

    public boolean hasActionRemaining() {
        return actionsRemaining > 0 || extraActionsRemaining > 0;
    }

    public boolean trySpendAction() {
        if (actionsRemaining > 0) {
            actionsRemaining--;
            return true;
        }
        if (extraActionsRemaining > 0) {
            extraActionsRemaining--;
            return true;
        }
        return false;
    }

    public void resetForTurn() {
        actionsRemaining = actionsMax;
        extraActionsRemaining = extraActionsMax;
    }
}
