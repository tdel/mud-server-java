package app.domain.actor.event;

import app.domain.actor.AbstractCharacter;
import app.domain.actor.component.ActiveEffect;

public record CharacterEffectExpired(AbstractCharacter character, ActiveEffect effect) {
}
