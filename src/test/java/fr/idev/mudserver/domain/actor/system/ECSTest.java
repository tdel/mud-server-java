package fr.idev.mudserver.domain.actor.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import fr.idev.mudserver.game.ECS;
import fr.idev.mudserver.game.Query;
import org.junit.jupiter.api.Test;

import fr.idev.mudserver.domain.actor.AbstractObject;
import fr.idev.mudserver.domain.actor.component.IdentityComponent;
import fr.idev.mudserver.domain.actor.component.MovementComponent;
import fr.idev.mudserver.domain.actor.instance.MonsterInstance;
import fr.idev.mudserver.domain.map.HexDirection;

class ECSTest {

    @Test
    void executeReturnsOnlyEntitiesMatchingASingleRequirement() {
        ECS ecs = new ECS();
        MonsterInstance withIdentity = new MonsterInstance(UUID.randomUUID());
        withIdentity.attachComponent(new IdentityComponent("Gobelin", 0));
        MonsterInstance withoutIdentity = new MonsterInstance(UUID.randomUUID());
        ecs.register(withIdentity);
        ecs.register(withoutIdentity);

        Query query = ecs.createQuery().addRequirement(IdentityComponent.class);
        List<AbstractObject> result = ecs.execute(query);

        assertThat(result).containsExactly(withIdentity);
    }

    @Test
    void executeOnlyReturnsEntitiesMatchingAllRequirements() {
        ECS ecs = new ECS();

        MonsterInstance both = new MonsterInstance(UUID.randomUUID());
        both.attachComponent(new IdentityComponent("Both", 0));
        both.attachComponent(new MovementComponent(HexDirection.E, 1, System.currentTimeMillis()));

        MonsterInstance onlyIdentity = new MonsterInstance(UUID.randomUUID());
        onlyIdentity.attachComponent(new IdentityComponent("OnlyIdentity", 0));

        MonsterInstance neither = new MonsterInstance(UUID.randomUUID());

        ecs.register(both);
        ecs.register(onlyIdentity);
        ecs.register(neither);

        Query query = ecs.createQuery().addRequirement(IdentityComponent.class).addRequirement(MovementComponent.class);

        assertThat(ecs.execute(query)).containsExactly(both);
    }

    @Test
    void unregisterRemovesEntityFromFutureQueries() {
        ECS ecs = new ECS();
        MonsterInstance monster = new MonsterInstance(UUID.randomUUID());
        monster.attachComponent(new IdentityComponent("Loup", 0));
        ecs.register(monster);

        ecs.unregister(monster);

        assertThat(ecs.execute(ecs.createQuery().addRequirement(IdentityComponent.class))).isEmpty();
    }

    @Test
    void registeringTheSameEntityTwiceDoesNotDuplicateResults() {
        ECS ecs = new ECS();
        MonsterInstance monster = new MonsterInstance(UUID.randomUUID());
        monster.attachComponent(new IdentityComponent("Loup", 0));

        ecs.register(monster);
        ecs.register(monster);

        assertThat(ecs.execute(ecs.createQuery().addRequirement(IdentityComponent.class))).containsExactly(monster);
    }

    @Test
    void queryWithNoRequirementsMatchesEveryRegisteredEntity() {
        ECS ecs = new ECS();
        MonsterInstance first = new MonsterInstance(UUID.randomUUID());
        MonsterInstance second = new MonsterInstance(UUID.randomUUID());
        ecs.register(first);
        ecs.register(second);

        assertThat(ecs.execute(ecs.createQuery())).containsExactlyInAnyOrder(first, second);
    }
}
