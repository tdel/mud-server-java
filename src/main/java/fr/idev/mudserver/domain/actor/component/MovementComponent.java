package fr.idev.mudserver.domain.actor.component;

import fr.idev.mudserver.domain.map.HexDirection;

public record MovementComponent(HexDirection direction, int cellsRemaining, long lastStepAt) {
    public MovementComponent withRemaining(int newRemaining, long stepAt) {
        return new MovementComponent(direction, newRemaining, stepAt);
    }
}
