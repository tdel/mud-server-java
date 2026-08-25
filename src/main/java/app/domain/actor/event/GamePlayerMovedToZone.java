package app.domain.actor.event;

import app.domain.actor.instance.CharacterInstance;
import app.domain.world.ZoneInstance;

public record GamePlayerMovedToZone(CharacterInstance character, ZoneInstance from, ZoneInstance to) {
}
