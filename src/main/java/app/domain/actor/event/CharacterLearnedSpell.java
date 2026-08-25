package app.domain.actor.event;

import app.domain.Spell;
import app.domain.actor.instance.CharacterInstance;

public record CharacterLearnedSpell(CharacterInstance character, Spell spell, Spell previousTier) {
}
