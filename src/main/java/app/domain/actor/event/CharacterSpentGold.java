package app.domain.actor.event;

import app.domain.actor.instance.PlayerInstance;

public record CharacterSpentGold(PlayerInstance character, int amount) {
}
