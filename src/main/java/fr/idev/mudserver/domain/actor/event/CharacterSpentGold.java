package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.actor.instance.CharacterInstance;

public record CharacterSpentGold(CharacterInstance character, int amount) {
}
