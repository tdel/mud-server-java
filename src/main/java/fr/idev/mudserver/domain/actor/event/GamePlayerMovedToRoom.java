package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.RoomInstance;

public record GamePlayerMovedToRoom(GamePlayer character, RoomInstance from, RoomInstance to) {
}
