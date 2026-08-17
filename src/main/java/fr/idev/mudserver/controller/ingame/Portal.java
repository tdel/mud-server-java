package fr.idev.mudserver.controller.ingame;

import java.util.Optional;
import java.util.Set;

import fr.idev.mudserver.domain.actor.component.PositionComponent;
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

    public Portal(Look lookAction) {
        this.lookAction = lookAction;
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
        character.moveToRoom(portal.targetRoom(), portal.targetCell()); // must be inside a System !
        lookAction.onReceive(connection, "");
    }
}
