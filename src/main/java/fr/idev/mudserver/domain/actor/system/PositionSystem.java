package fr.idev.mudserver.domain.actor.system;

import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.actor.component.PositionComponent;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.actor.event.GamePlayerMovedToRoom;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.world.RoomInstance;

@Service
public class PositionSystem {

    public void moveToRoom(CharacterInstance character, RoomInstance destination) {
        moveToRoom(character, destination, destination.getSpawnCell());
    }

    public void moveToRoom(CharacterInstance character, RoomInstance destination, HexCoordinate targetCell) {
        RoomInstance previous = character.component(PositionComponent.class).currentRoom();
        previous.leave(character);
        destination.join(character, targetCell);
        DomainEventPublisher.publish(new GamePlayerMovedToRoom(character, previous, destination));
    }
}
