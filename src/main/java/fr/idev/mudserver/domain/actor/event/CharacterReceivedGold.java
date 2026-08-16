package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.actor.instance.CharacterInstance;

public record CharacterReceivedGold(CharacterInstance character, int amount) {
}
