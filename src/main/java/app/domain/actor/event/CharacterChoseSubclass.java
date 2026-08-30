package app.domain.actor.event;

import app.domain.actor.Subclass;
import app.domain.actor.instance.CharacterInstance;

public record CharacterChoseSubclass(CharacterInstance character, int tier, Subclass subclass) {
}
