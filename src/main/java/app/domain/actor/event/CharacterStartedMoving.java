package app.domain.actor.event;

import app.domain.actor.AbstractCharacter;

public record CharacterStartedMoving(AbstractCharacter character) {
}
