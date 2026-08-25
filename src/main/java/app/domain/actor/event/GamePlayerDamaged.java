package app.domain.actor.event;

import app.domain.actor.AbstractCharacter;
import app.domain.actor.instance.CharacterInstance;

public record GamePlayerDamaged(CharacterInstance character, AbstractCharacter attacker, int amount) {
}
