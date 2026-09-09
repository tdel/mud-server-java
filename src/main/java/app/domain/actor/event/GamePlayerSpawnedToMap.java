package app.domain.actor.event;

import app.domain.actor.instance.PlayerInstance;
import app.domain.world.MapInstance;

public record GamePlayerSpawnedToMap(PlayerInstance character, MapInstance map) {
}
