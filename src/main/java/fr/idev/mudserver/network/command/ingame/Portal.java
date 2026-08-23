package fr.idev.mudserver.network.command.ingame;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.network.CommandHandler;
import fr.idev.mudserver.domain.world.ZonePortal;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.ingame.NoPortalHere;
import fr.idev.mudserver.network.message.ingame.ZoneMap;

@Component
public class Portal implements CommandHandler {

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
        Optional<ZonePortal> portal = character.getCurrentZone().findPortalAt(character.getPosition());
        if (portal.isEmpty()) {
            connection.send(new NoPortalHere());
            return;
        }

        character.moveToZone(portal.get().targetZone(), portal.get().targetCell());
        connection.send(new ZoneMap(character.getCurrentZone()));
        lookAction.onReceive(connection, "");
    }
}
