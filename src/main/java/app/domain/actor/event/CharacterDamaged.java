package app.domain.actor.event;

import app.domain.actor.AbstractCharacter;

public record CharacterDamaged(AbstractCharacter character, AbstractCharacter attacker, int amount) {
}
