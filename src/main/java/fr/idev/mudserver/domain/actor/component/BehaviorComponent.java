package fr.idev.mudserver.domain.actor.component;

import java.util.UUID;

public record BehaviorComponent(UUID currentTargetId) {

    public static BehaviorComponent idle() {
        return new BehaviorComponent(null);
    }
}
