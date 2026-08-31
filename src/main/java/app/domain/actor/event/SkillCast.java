package app.domain.actor.event;

import java.time.Instant;

import app.domain.ActiveSkill;
import app.domain.actor.AbstractCharacter;

public record SkillCast(AbstractCharacter caster, ActiveSkill activeSkill, AbstractCharacter target, int amount,
        boolean targetDefeated, boolean hit, Instant expiresAt) {
}
