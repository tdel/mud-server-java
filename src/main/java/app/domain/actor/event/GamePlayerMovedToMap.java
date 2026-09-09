package app.domain.actor.event;

import app.domain.actor.instance.PlayerInstance;
import app.domain.world.MapInstance;

public record GamePlayerMovedToMap(PlayerInstance character, MapInstance from, MapInstance to) {
}
