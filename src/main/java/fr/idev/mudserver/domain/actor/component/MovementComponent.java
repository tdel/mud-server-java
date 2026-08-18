package fr.idev.mudserver.domain.actor.component;

import fr.idev.mudserver.domain.map.HexDirection;

public class MovementComponent {

    public HexDirection direction;
    public int cellsRemaining;
    public long lastStepAt;

    public MovementComponent(HexDirection direction, int cellsRemaining, long lastStepAt) {
        this.direction = direction;
        this.cellsRemaining = cellsRemaining;
        this.lastStepAt = lastStepAt;
    }
}
