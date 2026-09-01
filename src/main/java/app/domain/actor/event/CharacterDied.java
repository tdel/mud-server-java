package app.domain.actor.event;

import app.domain.actor.AbstractCharacter;

public record CharacterDied(AbstractCharacter character, AbstractCharacter killer) {
}
