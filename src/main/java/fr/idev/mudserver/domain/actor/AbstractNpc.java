package fr.idev.mudserver.domain.actor;

import java.util.UUID;

public class AbstractNpc extends AbstractCharacter {

    // Composants requis en plus (voir AbstractCharacter) : PositionComponent,
    // NpcDescriptorComponent,
    // DialogueComponent (optionnel)
    public AbstractNpc(UUID id) {
        super(id);
    }
}
