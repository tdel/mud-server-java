package app.domain.actor.event;

import java.time.Instant;

import app.domain.Spell;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.instance.CharacterInstance;

public record SpellCast(CharacterInstance caster, Spell spell, AbstractCharacter target, int amount,
        boolean targetDefeated, boolean hit, Instant expiresAt) {
}
