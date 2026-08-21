package fr.idev.mudserver.domain.actor.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.Spell;
import fr.idev.mudserver.domain.SpellEffectType;
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.world.RoomInstance;
import fr.idev.mudserver.domain.world.RoomTemplate;
import fr.idev.mudserver.domain.world.WorldInstance;

class SpellCastingTest {

    @BeforeEach
    void setUpEventPublisher() {
        DomainEventPublisher.initialize(event -> {
        });
    }

    private CharacterInstance newCharacter(String name, CharacterClass characterClass, int currentHealth,
            int maxHealth) {
        WorldInstance world = new WorldInstance(UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        RoomTemplate roomTemplate = new RoomTemplate(UUID.randomUUID(), "Room", "desc", true, 3, 3,
                new HexCoordinate(0, 0), List.of());
        RoomInstance room = new RoomInstance(UUID.randomUUID(), roomTemplate, world);
        Account account = new Account(UUID.randomUUID(), "login", "hash", null);

        Map<Attribute, Integer> attributes = new EnumMap<>(Attribute.class);
        for (Attribute attribute : Attribute.values()) {
            attributes.put(attribute, 14);
        }

        return new CharacterInstance(UUID.randomUUID(), account, name, room, Gender.MAN, Race.HUMAN, characterClass, 1,
                currentHealth, maxHealth, attributes, 0, 0);
    }

    private Spell damageSpell() {
        return new Spell(UUID.randomUUID(), "Fire Bolt", "desc", 1, 3, 3, 6, SpellEffectType.DAMAGE, "1d1",
                Set.of(CharacterClass.WIZARD));
    }

    private Spell healingSpell() {
        return new Spell(UUID.randomUUID(), "Cure Wounds", "desc", 1, 5, 6, 0, SpellEffectType.HEALING, "1d1+3",
                Set.of(CharacterClass.CLERIC));
    }

    @Test
    void castDamageSpellAppliesDamageToTarget() {
        CharacterInstance caster = newCharacter("Mage", CharacterClass.WIZARD, 10, 10);
        CharacterInstance target = newCharacter("Enemy", CharacterClass.FIGHTER, 10, 10);
        Spell spell = damageSpell();

        SpellCasting.CastOutcome outcome = caster.getSpellCasting().cast(spell, target);

        assertThat(outcome.selfHeal()).isFalse();
        assertThat(outcome.amount()).isEqualTo(1);
        assertThat(target.getCurrentHealth()).isEqualTo(9);
        assertThat(outcome.targetHealthAfter()).isEqualTo(9);
        assertThat(outcome.targetDefeated()).isFalse();
    }

    @Test
    void castHealingSpellHealsCasterInsteadOfTarget() {
        CharacterInstance caster = newCharacter("Cleric", CharacterClass.CLERIC, 5, 10);
        Spell spell = healingSpell();

        SpellCasting.CastOutcome outcome = caster.getSpellCasting().cast(spell, caster);

        assertThat(outcome.selfHeal()).isTrue();
        assertThat(caster.getCurrentHealth()).isEqualTo(9);
        assertThat(outcome.targetHealthAfter()).isEqualTo(9);
    }

    @Test
    void castPutsSpellOnCooldownUntilItExpires() {
        CharacterInstance caster = newCharacter("Mage", CharacterClass.WIZARD, 10, 10);
        CharacterInstance target = newCharacter("Enemy", CharacterClass.FIGHTER, 10, 10);
        Spell spell = damageSpell();

        assertThat(caster.getSpellCasting().isReady(spell.id())).isTrue();

        caster.getSpellCasting().cast(spell, target);

        assertThat(caster.getSpellCasting().isReady(spell.id())).isFalse();
        assertThat(caster.getSpellCasting().remainingCooldown(spell.id())).isGreaterThan(Duration.ZERO);
    }
}
