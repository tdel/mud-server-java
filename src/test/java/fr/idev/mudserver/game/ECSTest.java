package fr.idev.mudserver.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import fr.idev.mudserver.domain.actor.instance.MonsterInstance;

class ECSTest {

    record Foo(String value) {
    }

    record Bar(String value) {
    }

    @Test
    void attachBeforeRegisterIsNotQueryableYet() {
        ECS ecs = new ECS();
        MonsterInstance entity = new MonsterInstance(UUID.randomUUID(), ecs);
        entity.attachComponent(new Foo("f"));

        Query query = ecs.createQuery().addRequirement(Foo.class);

        assertThat(ecs.execute(query)).isEmpty();
        assertThat(entity.findComponent(Foo.class)).contains(new Foo("f"));
    }

    @Test
    void attachBeforeRegisterBecomesQueryableOnceRegistered() {
        ECS ecs = new ECS();
        MonsterInstance entity = new MonsterInstance(UUID.randomUUID(), ecs);
        entity.attachComponent(new Foo("f"));

        ecs.register(entity);

        Query query = ecs.createQuery().addRequirement(Foo.class);
        List<QueryResult> results = ecs.execute(query);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().entity()).isEqualTo(entity);
        assertThat(results.getFirst().component(Foo.class)).isEqualTo(new Foo("f"));
    }

    @Test
    void attachAfterRegisterIsIndexed() {
        ECS ecs = new ECS();
        MonsterInstance entity = new MonsterInstance(UUID.randomUUID(), ecs);
        ecs.register(entity);

        entity.attachComponent(new Foo("f"));

        List<QueryResult> results = ecs.execute(ecs.createQuery().addRequirement(Foo.class));
        assertThat(results).hasSize(1);
    }

    @Test
    void detachRemovesFromIndex() {
        ECS ecs = new ECS();
        MonsterInstance entity = new MonsterInstance(UUID.randomUUID(), ecs);
        ecs.register(entity);
        entity.attachComponent(new Foo("f"));

        entity.detachComponent(Foo.class);

        assertThat(ecs.execute(ecs.createQuery().addRequirement(Foo.class))).isEmpty();
    }

    @Test
    void unregisterClearsIndex() {
        ECS ecs = new ECS();
        MonsterInstance entity = new MonsterInstance(UUID.randomUUID(), ecs);
        ecs.register(entity);
        entity.attachComponent(new Foo("f"));

        ecs.unregister(entity);

        assertThat(ecs.execute(ecs.createQuery().addRequirement(Foo.class))).isEmpty();
    }

    @Test
    void queryIntersectsMultipleRequirements() {
        ECS ecs = new ECS();
        MonsterInstance both = new MonsterInstance(UUID.randomUUID(), ecs);
        MonsterInstance onlyFoo = new MonsterInstance(UUID.randomUUID(), ecs);
        ecs.register(both);
        ecs.register(onlyFoo);
        both.attachComponent(new Foo("f"));
        both.attachComponent(new Bar("b"));
        onlyFoo.attachComponent(new Foo("f"));

        List<QueryResult> results = ecs.execute(ecs.createQuery().addRequirement(Foo.class).addRequirement(Bar.class));

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().entity()).isEqualTo(both);
    }

    @Test
    void queryWithNoMatchReturnsEmpty() {
        ECS ecs = new ECS();
        MonsterInstance entity = new MonsterInstance(UUID.randomUUID(), ecs);
        ecs.register(entity);
        entity.attachComponent(new Foo("f"));

        assertThat(ecs.execute(ecs.createQuery().addRequirement(Bar.class))).isEmpty();
    }

    @Test
    void queryWithNoRequirementReturnsAllRegisteredEntities() {
        ECS ecs = new ECS();
        MonsterInstance entity = new MonsterInstance(UUID.randomUUID(), ecs);
        ecs.register(entity);

        List<QueryResult> results = ecs.execute(ecs.createQuery());

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().entity()).isEqualTo(entity);
    }
}
