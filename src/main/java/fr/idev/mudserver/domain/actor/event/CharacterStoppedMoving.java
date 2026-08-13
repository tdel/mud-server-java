package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.actor.GameCharacter;

public record CharacterStoppedMoving(GameCharacter character) {
}
