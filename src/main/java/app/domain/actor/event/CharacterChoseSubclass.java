package app.domain.actor.event;

import app.domain.actor.Subclass;
import app.domain.actor.instance.PlayerInstance;

public record CharacterChoseSubclass(PlayerInstance character, int tier, Subclass subclass) {
}
