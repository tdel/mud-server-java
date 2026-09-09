package app.domain.actor.event;

import app.domain.actor.instance.PlayerInstance;

public record CharacterReceivedGold(PlayerInstance character, int amount) {
}
