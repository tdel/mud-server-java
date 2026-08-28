package app.domain.actor.event;

import app.domain.actor.instance.CharacterInstance;

public record GamePlayerRespawned(CharacterInstance character) {
}
