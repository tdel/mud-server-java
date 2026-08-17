package fr.idev.mudserver.domain.actor.component;

import fr.idev.mudserver.domain.actor.AbstractCharacter;

public record CombatComponent(int currentHealth, int maxHealth, AbstractCharacter target, int actionsMax,
        int extraActionsMax, int actionsRemaining, int extraActionsRemaining) {

    public static final int DEFAULT_ACTIONS_MAX = 1;
    public static final int DEFAULT_EXTRA_ACTIONS_MAX = 0;
}
