package app.network.command.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import app.domain.actor.instance.CharacterInstance;
import app.domain.world.ZoneInstance;
import app.network.CommandHandler;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.ingame.CharacterNotDead;

@Component
public class Respawn implements CommandHandler {

    @Override
    public String name() {
        return "respawn";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();

        if (character.getCurrentHealth() > 0) {
            connection.send(new CharacterNotDead());
            return;
        }

        ZoneInstance startingZone = character.getWorldInstance().startingZoneInstance()
                .orElseThrow(() -> new IllegalStateException("Aucune starting zone configurée"));
        character.respawn(startingZone, startingZone.getSpawnPosition());
    }
}
