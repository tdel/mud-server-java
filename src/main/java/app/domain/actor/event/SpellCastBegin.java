package app.domain.actor.event;

import app.domain.Spell;
import app.domain.actor.AbstractCharacter;

public record SpellCastBegin(AbstractCharacter caster, Spell spell, AbstractCharacter target) {
}
