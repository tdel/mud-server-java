package fr.idev.mudserver.domain.actor;

import java.util.UUID;

import fr.idev.mudserver.game.ECS;

public abstract class AbstractCharacter extends AbstractObject {

    // Composants requis en plus (voir AbstractObject) : AttributeComponent,
    // HealthComponent, CombatComponent, MovementComponent
    protected AbstractCharacter(UUID id, ECS ecs) {
        super(id, ecs);
    }
}
