package app.domain.actor.event;

import app.domain.actor.instance.CharacterInstance;

public record CharacterSpentGold(CharacterInstance character, int amount) {
}
