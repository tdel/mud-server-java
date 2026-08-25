package app.domain.actor.event;

import app.domain.actor.instance.CharacterInstance;

public record NewGamePlayerCreated(CharacterInstance character) {
}
