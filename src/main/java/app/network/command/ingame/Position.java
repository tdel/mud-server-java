package app.network.command.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import app.domain.actor.instance.PlayerInstance;
import app.game.engine.MovementEngine;
import app.network.CommandHandler;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.ingame.PositionUpdated;

/**
 * Le serveur ne pousse plus la position du personnage à chaque tick de
 * déplacement (voir MovementEngine) : le client interpole localement et appelle
 * cette commande à intervalle régulier (ex. 1x/s) pour corriger toute dérive,
 * plutôt que de recevoir un flux poussé. La position renvoyée est projetée à
 * l'instant de la commande (voir MovementEngine.currentPosition) et non celle
 * du dernier tick, jusqu'à 100 ms plus ancienne.
 */
@Component
public class Position implements CommandHandler {

    private final MovementEngine movementEngine;

    public Position(MovementEngine movementEngine) {
        this.movementEngine = movementEngine;
    }

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
        PlayerInstance character = connection.character();
        app.domain.map.Position position = movementEngine.currentPosition(character);
        connection.send(new PositionUpdated(position.x(), position.y(), character.getMotionSystem().getHeading()));
    }
}
