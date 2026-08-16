package fr.idev.mudserver.domain.actor.component;

import fr.idev.mudserver.domain.map.HexDirection;

public record MovementComponent(int speed, ActiveMovement activeMovement) {

    public MovementComponent(int speed) {
        this(speed, null);
    }

    public record ActiveMovement(HexDirection direction, int cellsRemaining, long lastStepAt) {
        public ActiveMovement withRemaining(int newRemaining, long stepAt) {
            return new ActiveMovement(direction, newRemaining, stepAt);
        }
    }
}
