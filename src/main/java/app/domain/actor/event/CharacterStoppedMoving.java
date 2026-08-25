package app.domain.actor.event;

import app.domain.actor.AbstractCharacter;

public record CharacterStoppedMoving(AbstractCharacter character) {
}
