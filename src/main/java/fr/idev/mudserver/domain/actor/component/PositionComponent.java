package fr.idev.mudserver.domain.actor.component;

import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.world.RoomInstance;

public record PositionComponent(RoomInstance currentRoom, HexCoordinate hexCoordinate) {
}
