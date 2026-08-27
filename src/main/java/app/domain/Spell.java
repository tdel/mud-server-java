package app.domain;

import java.util.Set;
import java.util.UUID;

import app.domain.actor.CharacterClass;
import app.domain.actor.ModifiedStat;

public record Spell(UUID id, String name, int tier, String description, int requiredLevel, int manaCost,
        int cooldownSeconds, int castingTimeMs, int range, SpellEffectType effect, String effectDice,
        boolean projectile, int projectileSpeed, Set<CharacterClass> classes, ModifiedStat modifiedStat,
        int durationSeconds) {
}
