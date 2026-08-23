package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.component.ActiveEffect;

public record CharacterEffectExpired(AbstractCharacter character, ActiveEffect effect) {
}
