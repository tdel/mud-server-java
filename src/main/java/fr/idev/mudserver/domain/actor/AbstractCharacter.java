package fr.idev.mudserver.domain.actor;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import fr.idev.mudserver.domain.actor.component.AttributeComponent;
import fr.idev.mudserver.domain.actor.component.CombatComponent;
import fr.idev.mudserver.domain.actor.component.MovementComponent;

public abstract class AbstractCharacter extends AbstractObject {

    protected AbstractCharacter(UUID id, String name, Map<Attribute, Integer> attributes, int currentHealth,
            int maxHealth, int speed) {
        super(id, name);
        this.attachComponent(new AttributeComponent(new EnumMap<>(attributes)));
        this.attachComponent(new CombatComponent(currentHealth, maxHealth, null, CombatComponent.DEFAULT_ACTIONS_MAX,
                CombatComponent.DEFAULT_EXTRA_ACTIONS_MAX, CombatComponent.DEFAULT_ACTIONS_MAX,
                CombatComponent.DEFAULT_EXTRA_ACTIONS_MAX));
        this.attachComponent(new MovementComponent(speed));
    }
}
