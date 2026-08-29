package app.domain.actor.event;

import app.domain.actor.instance.CharacterInstance;

public record PlayerRemovedFromWorld(CharacterInstance character) {
}
