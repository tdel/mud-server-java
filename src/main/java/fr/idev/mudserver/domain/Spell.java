package fr.idev.mudserver.domain;

import java.util.Set;
import java.util.UUID;

import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.ModifiedStat;

public record Spell(UUID id, String name, String description, int requiredLevel, int manaCost, int cooldownSeconds,
        int range, SpellEffectType effect, String effectDice, Set<CharacterClass> classes, ModifiedStat modifiedStat,
        int durationSeconds) {
}
