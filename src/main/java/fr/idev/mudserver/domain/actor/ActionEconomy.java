package fr.idev.mudserver.domain.actor;

/**
 * Budget d'actions d'un {@link GameCharacter} pour le tour de combat en cours.
 * Deux pools indépendants — {@code actions} (1 par défaut) et
 * {@code extraActions} (0 par défaut, la terminologie propre à ce projet pour
 * les compétences façon Extra Attack) — chacun avec un plafond permanent et un
 * solde propre au tour courant. Les deux surfaces de mutation restent
 * volontairement distinctes :
 * {@link #setActionsMax}/{@link #setExtraActionsMax} pour un octroi permanent
 * (futur feat/feature), qui ne prend effet qu'au prochain
 * {@link #resetForTurn()} ; {@link #grantBonusActionThisTurn}/
 * {@link #grantBonusExtraActionsThisTurn} pour un octroi ponctuel (futur Action
 * Surge / sort), qui agit immédiatement sur le solde et disparaît au reset
 * suivant puisqu'il ne touche jamais le plafond.
 */
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

    /**
     * Consomme une action normale en priorité, une action extra sinon ;
     * {@code false} si le budget est épuisé — ne devrait pas arriver tant que
     * {@code CombatEncounter#currentParticipant()} désigne encore l'appelant.
     */
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

    /**
     * Début de tour : les soldes reviennent aux plafonds, tout octroi ponctuel du
     * tour précédent est perdu.
     */
    public void resetForTurn() {
        actionsRemaining = actionsMax;
        extraActionsRemaining = extraActionsMax;
    }
}
