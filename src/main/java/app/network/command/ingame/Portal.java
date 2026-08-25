package app.network.command.ingame;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import app.network.CommandHandler;
import app.domain.world.ZonePortal;
import app.domain.actor.instance.CharacterInstance;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.ingame.NoPortalHere;
import app.network.message.ingame.ViewAround;
import app.network.message.ingame.ZoneMap;

@Component
public class Portal implements CommandHandler {

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

        character.moveToZone(portal.get().targetZone(), portal.get().targetPosition());
        connection.send(new ZoneMap(character.getCurrentZone()));
        connection.send(new ViewAround(character));
    }
}
