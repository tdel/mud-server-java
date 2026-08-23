package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.world.ZoneInstance;

public record GamePlayerSpawnedToZone(CharacterInstance character, ZoneInstance zone) {
}
