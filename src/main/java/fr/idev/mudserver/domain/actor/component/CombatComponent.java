package fr.idev.mudserver.domain.actor.component;

import fr.idev.mudserver.domain.actor.AbstractCharacter;

public class CombatComponent {

    public static final int DEFAULT_ACTIONS_MAX = 1;
    public static final int DEFAULT_EXTRA_ACTIONS_MAX = 0;

    public AbstractCharacter target;
    public int actionsMax;
    public int extraActionsMax;
    public int actionsRemaining;
    public int extraActionsRemaining;

    public CombatComponent(AbstractCharacter target, int actionsMax, int extraActionsMax, int actionsRemaining,
            int extraActionsRemaining) {
        this.target = target;
        this.actionsMax = actionsMax;
        this.extraActionsMax = extraActionsMax;
        this.actionsRemaining = actionsRemaining;
        this.extraActionsRemaining = extraActionsRemaining;
    }
}
