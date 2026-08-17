package fr.idev.mudserver.controller.ingame;

import java.util.Optional;
import java.util.Set;

import fr.idev.mudserver.domain.actor.component.PositionComponent;
import fr.idev.mudserver.domain.actor.system.PositionSystem;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.world.RoomPortal;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.ingame.NoPortalHere;

@Component
public class Portal implements ControllerHandler {

    private final Look lookAction;
    private final PositionSystem positionSystem;

    public Portal(Look lookAction, PositionSystem positionSystem) {
        this.lookAction = lookAction;
        this.positionSystem = positionSystem;
    }

    @Override
    public String name() {
        return "portal";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();
        PositionComponent position = character.component(PositionComponent.class);
        Optional<RoomPortal> portalQuery = position.currentRoom().findPortalAt(position.hexCoordinate());
        if (portalQuery.isEmpty()) {
            connection.send(new NoPortalHere());
            return;
        }

        RoomPortal portal = portalQuery.get();
        positionSystem.moveToRoom(character, portal.targetRoom(), portal.targetCell());
        lookAction.onReceive(connection, "");
    }
}
