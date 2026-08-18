package fr.idev.mudserver.domain.actor;

import java.util.UUID;

import fr.idev.mudserver.game.ECS;

public class AbstractNpc extends AbstractCharacter {

    // Composants requis en plus (voir AbstractCharacter) : PositionComponent,
    // NpcDescriptorComponent,
    // DialogueComponent (optionnel)
    public AbstractNpc(UUID id, ECS ecs) {
        super(id, ecs);
    }
}
