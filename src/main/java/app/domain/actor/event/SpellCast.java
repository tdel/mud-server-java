package app.domain.actor.event;

import java.time.Instant;

import app.domain.Spell;
import app.domain.actor.AbstractCharacter;

public record SpellCast(AbstractCharacter caster, Spell spell, AbstractCharacter target, int amount,
        boolean targetDefeated, boolean hit, Instant expiresAt) {
}
