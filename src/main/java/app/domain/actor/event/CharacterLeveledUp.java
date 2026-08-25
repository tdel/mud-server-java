package app.domain.actor.event;

import app.domain.actor.instance.CharacterInstance;

public record CharacterLeveledUp(CharacterInstance character, int newLevel, int hpGained) {
}
