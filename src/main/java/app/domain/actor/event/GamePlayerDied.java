package app.domain.actor.event;

import app.domain.actor.AbstractCharacter;
import app.domain.actor.instance.CharacterInstance;

public record GamePlayerDied(CharacterInstance character, AbstractCharacter killer) {
}
