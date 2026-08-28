package app.domain.actor.instance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import app.domain.Account;
import app.domain.actor.Attribute;
import app.domain.actor.CharacterClass;
import app.domain.actor.Gender;
import app.domain.actor.Race;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.GamePlayerDied;
import app.domain.actor.event.GamePlayerRespawned;
import app.domain.map.Position;
import app.domain.world.CollisionGrid;
import app.domain.world.WorldInstance;
import app.domain.world.ZoneInstance;
import app.domain.world.ZoneTemplate;

class CharacterInstanceTest {

    private final List<Object> publishedEvents = new ArrayList<>();

    @BeforeEach
    void initEventPublisher() {
        publishedEvents.clear();
        DomainEventPublisher.initialize(publishedEvents::add);
    }

    @Test
    void takeDamageClampsHealthAtZeroAndPublishesDeathOnlyOnce() {
        CharacterInstance character = newCharacter(newZone(true));

        boolean firstHit = character.takeDamage(50, character);
        boolean secondHit = character.takeDamage(1, character);

        assertThat(firstHit).isTrue();
        assertThat(secondHit).isFalse();
        assertThat(character.getCurrentHealth()).isZero();
        assertThat(publishedEvents.stream().filter(GamePlayerDied.class::isInstance)).hasSize(1);
    }

    @Test
    void takeDamageClearsCombatTargetOnDeath() {
        CharacterInstance character = newCharacter(newZone(true));
        CharacterInstance attacker = newCharacter(newZone(true));
        character.getCombat().setTarget(attacker);

        character.takeDamage(50, attacker);

        assertThat(character.getCombat().getTarget()).isNull();
    }

    @Test
    void respawnRestoresAQuarterOfHealthAndResetsMana() {
        ZoneInstance startingZone = newZone(true);
        CharacterInstance character = newCharacter(startingZone);
        character.setCurrentMana(character.getMaxMana());
        character.takeDamage(50, character);

        character.respawn(startingZone, startingZone.getSpawnPosition());

        assertThat(character.getCurrentHealth()).isEqualTo(Math.max(1, character.getMaxHealth() / 4));
        assertThat(character.getCurrentMana()).isZero();
        assertThat(character.getCurrentZone()).isEqualTo(startingZone);
        assertThat(character.getPosition()).isEqualTo(startingZone.getSpawnPosition());
        assertThat(publishedEvents.stream().filter(GamePlayerRespawned.class::isInstance)).hasSize(1);
    }

    private static CharacterInstance newCharacter(ZoneInstance zone) {
        Account account = new Account(UUID.randomUUID(), "login", "password");
        Map<Attribute, Integer> attributes = new EnumMap<>(Attribute.class);
        for (Attribute attribute : Attribute.values()) {
            attributes.put(attribute, 10);
        }

        CharacterInstance character = new CharacterInstance(UUID.randomUUID(), account, "Test Character", zone,
                Gender.MAN, Race.HUMAN, CharacterClass.FIGHTER, 1, 50, 50, attributes, 0, 0, 0, 10, 10, Set.of(),
                List.of());
        character.setWorldInstance(zone.getWorldInstance());
        zone.join(character);
        return character;
    }

    private static ZoneInstance newZone(boolean isStartingZone) {
        CollisionGrid terrain = new CollisionGrid(1, 1, 1.0, new BitSet());
        ZoneTemplate template = new ZoneTemplate(UUID.randomUUID(), "Town", "description", isStartingZone, terrain,
                new Position(0, 0), List.of(), List.of(), List.of());
        WorldInstance worldInstance = new WorldInstance(UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        ZoneInstance zone = new ZoneInstance(UUID.randomUUID(), template, worldInstance);
        worldInstance.setZoneInstances(Map.of(template.getId(), zone));
        return zone;
    }
}
