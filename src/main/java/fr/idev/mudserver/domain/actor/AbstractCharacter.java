package fr.idev.mudserver.domain.actor;

import java.util.UUID;

public abstract class AbstractCharacter extends AbstractObject {

    // Composants requis en plus (voir AbstractObject) : AttributeComponent,
    // CombatComponent, MovementComponent
    protected AbstractCharacter(UUID id) {
        super(id);
    }
}
