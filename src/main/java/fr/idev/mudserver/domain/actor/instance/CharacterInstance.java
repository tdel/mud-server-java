package fr.idev.mudserver.domain.actor.instance;

import java.util.UUID;

import fr.idev.mudserver.domain.actor.AbstractCharacter;

public final class CharacterInstance extends AbstractCharacter {

    public static final int MAX_SHORT_RESTS_BEFORE_LONG_REST = 2;

    // Composants requis en plus (voir AbstractCharacter) : AccountComponent,
    // PositionComponent, InventoryComponent,
    // LevelingComponent, RestComponent, AppearanceComponent
    public CharacterInstance(UUID id) {
        super(id);
    }

}
