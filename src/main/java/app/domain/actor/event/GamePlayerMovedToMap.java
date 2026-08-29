package app.domain.actor.event;

import app.domain.actor.instance.CharacterInstance;
import app.domain.world.MapInstance;

public record GamePlayerMovedToMap(CharacterInstance character, MapInstance from, MapInstance to) {
}
