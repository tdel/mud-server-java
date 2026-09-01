package app.network.command.ingame;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import app.domain.actor.instance.CharacterInstance;
import app.domain.world.MapInstance;
import app.network.CommandHandler;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.ingame.CharacterNotDead;
import app.network.message.ingame.MapEnter;
import app.network.message.ingame.MapView;
import app.network.message.ingame.StartingMapNotConfigured;

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

        Optional<MapInstance> startingMap = character.getWorldInstance().startingMapInstance();
        if (startingMap.isEmpty()) {
            connection.send(new StartingMapNotConfigured());
            return;
        }
        character.respawn(startingMap.get(), startingMap.get().getSpawnPosition());

        // La starting map peut différer de la map où le personnage est mort : sans
        // ça, le
        // client resterait affiché sur l'ancienne carte (voir Portal.java, même
        // besoin).
        connection.send(new MapView(character.getMotionSystem().getCurrentMap()));
        connection.send(new MapEnter(character));
    }
}
