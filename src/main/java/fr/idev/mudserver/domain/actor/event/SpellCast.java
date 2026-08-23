package fr.idev.mudserver.domain.actor.event;

import java.time.Instant;

import fr.idev.mudserver.domain.Spell;
import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;

public record SpellCast(CharacterInstance caster, Spell spell, AbstractCharacter target, int amount,
        boolean targetDefeated, boolean hit, Instant expiresAt) {
}
