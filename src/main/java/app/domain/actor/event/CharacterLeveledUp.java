package app.domain.actor.event;

import app.domain.actor.instance.PlayerInstance;

public record CharacterLeveledUp(PlayerInstance character, int newLevel, int hpGained) {
}
