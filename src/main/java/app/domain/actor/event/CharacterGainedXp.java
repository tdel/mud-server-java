package app.domain.actor.event;

import app.domain.actor.instance.PlayerInstance;

public record CharacterGainedXp(PlayerInstance character, int amount) {
}
