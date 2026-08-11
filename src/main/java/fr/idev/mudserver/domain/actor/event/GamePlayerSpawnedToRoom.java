package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.RoomInstance;

public record GamePlayerSpawnedToRoom(GamePlayer character, RoomInstance room) {
}
