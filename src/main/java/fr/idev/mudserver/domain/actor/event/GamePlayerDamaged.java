package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;

public record GamePlayerDamaged(CharacterInstance character, AbstractCharacter attacker, int amount) {
}
