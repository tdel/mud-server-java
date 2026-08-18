package fr.idev.mudserver.domain.actor.component;

import java.util.UUID;

public class BehaviorComponent {

    public UUID currentTargetId;

    public BehaviorComponent(UUID currentTargetId) {
        this.currentTargetId = currentTargetId;
    }

    public static BehaviorComponent idle() {
        return new BehaviorComponent(null);
    }
}
