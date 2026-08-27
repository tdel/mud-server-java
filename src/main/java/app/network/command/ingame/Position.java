package app.network.command.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import app.domain.actor.instance.CharacterInstance;
import app.network.CommandHandler;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.ingame.PositionUpdated;

/**
 * Le serveur ne pousse plus la position du personnage à chaque tick de
 * déplacement (voir MovementEngine) : le client interpole localement et appelle
 * cette commande à intervalle régulier (ex. 1x/s) pour corriger toute dérive,
 * plutôt que de recevoir un flux poussé.
 */
@Component
public class Position implements CommandHandler {

    @Override
    public String name() {
        return "position";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();
        connection.send(new PositionUpdated(character.getPosition().x(), character.getPosition().y()));
    }
}
