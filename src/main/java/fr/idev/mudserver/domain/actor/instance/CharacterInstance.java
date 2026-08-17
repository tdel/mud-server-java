package fr.idev.mudserver.domain.actor.instance;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.actor.*;
import fr.idev.mudserver.domain.actor.component.*;
import fr.idev.mudserver.domain.world.RoomInstance;
import fr.idev.mudserver.network.OutputMessage;

public final class CharacterInstance extends AbstractCharacter {

    public static final int MAX_SHORT_RESTS_BEFORE_LONG_REST = 2;

    public CharacterInstance(UUID id, Account account, String name, RoomInstance room, Gender gender, Race race,
            CharacterClass characterClass, int level, int currentHealth, int maxHealth,
            Map<Attribute, Integer> attributes, int xp, int gold) {
        this(id, account, name, room, gender, race, characterClass, level, currentHealth, maxHealth, attributes, xp,
                gold, 0);
    }

    public CharacterInstance(UUID id, Account account, String name, RoomInstance room, Gender gender, Race race,
            CharacterClass characterClass, int level, int currentHealth, int maxHealth,
            Map<Attribute, Integer> attributes, int xp, int gold, int shortRestCount) {
        super(id, name, attributes, currentHealth, maxHealth, race.speed());
        attachComponent(new AccountComponent(account));
        attachComponent(new PositionComponent(room, null)); // missing hexCoordinate here !
        attachComponent(new InventoryComponent(List.of(), gold));
        attachComponent(new LevelingComponent(level, xp));
        attachComponent(new RestComponent(shortRestCount));
        attachComponent(new AppearanceComponent(race, gender, characterClass));
    }

}
