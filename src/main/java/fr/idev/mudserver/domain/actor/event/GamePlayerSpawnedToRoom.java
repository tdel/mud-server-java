package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.world.RoomInstance;

public record GamePlayerSpawnedToRoom(CharacterInstance character, RoomInstance room) {
}
