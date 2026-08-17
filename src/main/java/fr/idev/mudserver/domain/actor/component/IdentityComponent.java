package fr.idev.mudserver.domain.actor.component;

import fr.idev.mudserver.domain.actor.system.MovementSystem;

public record IdentityComponent(String name, int speed) {

    public long cellSpeed() {
        return MovementSystem.REFERENCE_TIME_MS * MovementSystem.REFERENCE_SPEED / Math.max(1, speed);
    }
}
