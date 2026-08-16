package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.actor.AbstractCharacter;

public record CharacterStoppedMoving(AbstractCharacter character) {
}
