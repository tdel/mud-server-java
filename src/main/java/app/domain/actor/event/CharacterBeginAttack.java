package app.domain.actor.event;

import app.domain.actor.AbstractCharacter;

public record CharacterBeginAttack(AbstractCharacter attacker, AbstractCharacter defender) {
}
