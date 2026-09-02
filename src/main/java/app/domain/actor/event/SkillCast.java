package app.domain.actor.event;

import java.time.Instant;
import java.util.List;

import app.domain.ActiveSkill;
import app.domain.StatModifier;
import app.domain.actor.AbstractCharacter;

public record SkillCast(AbstractCharacter caster, ActiveSkill activeSkill, int level, AbstractCharacter target,
        int amount, boolean targetDefeated, boolean hit, Instant expiresAt, List<StatModifier> modifiers) {
}
