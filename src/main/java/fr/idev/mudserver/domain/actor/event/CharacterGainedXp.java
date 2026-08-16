package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.actor.instance.CharacterInstance;

public record CharacterGainedXp(CharacterInstance character, int amount) {
}
