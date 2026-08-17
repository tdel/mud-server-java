package fr.idev.mudserver.domain.actor.instance;

import java.util.UUID;

import fr.idev.mudserver.domain.actor.AbstractCharacter;

public final class MonsterInstance extends AbstractCharacter {

    // Composants requis en plus (voir AbstractCharacter) : BehaviorComponent,
    // LootComponent, AggroComponent,
    // PositionComponent, MonsterCombatComponent
    public MonsterInstance(UUID id) {
        super(id);
    }

}
