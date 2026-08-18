package fr.idev.mudserver.domain.actor.component;

import fr.idev.mudserver.domain.actor.system.MovementSystem;

public class IdentityComponent {

    public String name;
    public int speed;

    public IdentityComponent(String name, int speed) {
        this.name = name;
        this.speed = speed;
    }

    public long cellSpeed() {
        return MovementSystem.REFERENCE_TIME_MS * MovementSystem.REFERENCE_SPEED / Math.max(1, speed);
    }
}
