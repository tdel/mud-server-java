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
import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.ModifiedStat;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.world.ZoneInstance;
import fr.idev.mudserver.domain.world.ZoneTemplate;
import fr.idev.mudserver.domain.world.TileType;
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
        ZoneTemplate zoneTemplate = new ZoneTemplate(UUID.randomUUID(), "Zone", "desc", true, flatTerrain(3, 3),
                new HexCoordinate(0, 0), List.of(), List.of());
        ZoneInstance zone = new ZoneInstance(UUID.randomUUID(), zoneTemplate, world);
        Account account = new Account(UUID.randomUUID(), "login", "hash");

        Map<Attribute, Integer> attributes = new EnumMap<>(Attribute.class);
        for (Attribute attribute : Attribute.values()) {
            attributes.put(attribute, 14);
        }

        return new CharacterInstance(UUID.randomUUID(), account, name, zone, Gender.MAN, Race.HUMAN, characterClass, 1,
                currentHealth, maxHealth, attributes, 0, 0);
    }

    private Spell damageSpell() {
        return new Spell(UUID.randomUUID(), "Fire Bolt", "desc", 1, 3, 3, 6, SpellEffectType.DAMAGE, "1d1",
                Set.of(CharacterClass.WIZARD), null, 0);
    }

    private Spell healingSpell() {
        return new Spell(UUID.randomUUID(), "Cure Wounds", "desc", 1, 5, 6, 0, SpellEffectType.HEALING, "1d1+3",
                Set.of(CharacterClass.CLERIC), null, 0);
    }

    private Spell buffSpell() {
        return new Spell(UUID.randomUUID(), "Bless", "desc", 1, 5, 6, 5, SpellEffectType.BUFF, "1d1",
                Set.of(CharacterClass.CLERIC), ModifiedStat.ATTACK_ROLL, 60);
    }

    private Spell debuffSpell() {
        return new Spell(UUID.randomUUID(), "Bane", "desc", 1, 5, 6, 5, SpellEffectType.DEBUFF, "1d1",
                Set.of(CharacterClass.CLERIC), ModifiedStat.ATTACK_ROLL, 60);
    }

    // Le jet d'attaque de sort peut rater sur un jet naturel de 1 quel que soit le
    // bonus : on relance jusqu'au premier coup pour tester le cas "touché" sans
    // dépendre d'un mock de dé.
    private SpellCasting.CastOutcome castUntilHit(CharacterInstance caster, Spell spell, AbstractCharacter target) {
        SpellCasting.CastOutcome outcome;
        do {
            outcome = caster.getSpellCasting().cast(spell, target);
        } while (!outcome.hit());
        return outcome;
    }

    @Test
    void castDamageSpellAppliesDamageToTarget() {
        CharacterInstance caster = newCharacter("Mage", CharacterClass.WIZARD, 10, 10);
        CharacterInstance target = newCharacter("Enemy", CharacterClass.FIGHTER, 10, 10);
        Spell spell = damageSpell();

        SpellCasting.CastOutcome outcome = castUntilHit(caster, spell, target);

        assertThat(outcome.selfHeal()).isFalse();
        assertThat(outcome.amount()).isEqualTo(1);
        assertThat(target.getCurrentHealth()).isEqualTo(9);
        assertThat(outcome.targetHealthAfter()).isEqualTo(9);
        assertThat(outcome.targetDefeated()).isFalse();
    }

    @Test
    void castBuffSpellAppliesPositiveModifierToTarget() {
        CharacterInstance caster = newCharacter("Cleric", CharacterClass.CLERIC, 10, 10);
        Spell spell = buffSpell();

        SpellCasting.CastOutcome outcome = caster.getSpellCasting().cast(spell, caster);

        assertThat(outcome.hit()).isTrue();
        assertThat(outcome.amount()).isEqualTo(1);
        assertThat(outcome.effectExpiresAt()).isNotNull();
        assertThat(caster.getActiveEffects().totalModifier(ModifiedStat.ATTACK_ROLL)).isEqualTo(1);
    }

    @Test
    void castDebuffSpellThatHitsAppliesNegativeModifierToTarget() {
        CharacterInstance caster = newCharacter("Cleric", CharacterClass.CLERIC, 10, 10);
        CharacterInstance target = newCharacter("Enemy", CharacterClass.FIGHTER, 10, 10);
        Spell spell = debuffSpell();

        SpellCasting.CastOutcome outcome = castUntilHit(caster, spell, target);

        assertThat(outcome.amount()).isEqualTo(-1);
        assertThat(target.getActiveEffects().totalModifier(ModifiedStat.ATTACK_ROLL)).isEqualTo(-1);
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
    private static Map<HexCoordinate, TileType> flatTerrain(int width, int height) {
        Map<HexCoordinate, TileType> terrain = new java.util.HashMap<>();
        for (int q = 0; q < width; q++) {
            for (int r = 0; r < height; r++) {
                terrain.put(new HexCoordinate(q, r), TileType.FLOOR);
            }
        }
        return terrain;
    }
}
