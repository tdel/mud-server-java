package fr.idev.mudserver.domain.actor.component;

import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.world.RoomInstance;

public class PositionComponent {

    public RoomInstance currentRoom;
    public HexCoordinate hexCoordinate;

    public PositionComponent(RoomInstance currentRoom, HexCoordinate hexCoordinate) {
        this.currentRoom = currentRoom;
        this.hexCoordinate = hexCoordinate;
    }
}
