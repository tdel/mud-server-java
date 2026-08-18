package fr.idev.mudserver.domain.actor.instance;

import java.util.UUID;

import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.game.ECS;

public final class MonsterInstance extends AbstractCharacter {

    // Composants requis en plus (voir AbstractCharacter) : BehaviorComponent,
    // LootComponent, AggroComponent,
    // PositionComponent, MonsterCombatComponent, HealthComponent
    public MonsterInstance(UUID id, ECS ecs) {
        super(id, ecs);
    }

}
