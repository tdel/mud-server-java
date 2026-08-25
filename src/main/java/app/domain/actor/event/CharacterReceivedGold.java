package app.domain.actor.event;

import app.domain.actor.instance.CharacterInstance;

public record CharacterReceivedGold(CharacterInstance character, int amount) {
}
