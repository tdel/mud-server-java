package fr.idev.mudserver.network.command.ingame;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.map.GridPathfinder;
import fr.idev.mudserver.domain.map.Position;
import fr.idev.mudserver.game.engine.MovementEngine;
import fr.idev.mudserver.network.CommandHandler;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.MovementStarted;
import fr.idev.mudserver.network.message.ingame.NoPathToDestination;
import fr.idev.mudserver.network.message.ingame.ViewAround;

@Component
public class Goto implements CommandHandler {

    private final MovementEngine movementEngine;

    public Goto(MovementEngine movementEngine) {
        this.movementEngine = movementEngine;
    }

    @Override
    public String name() {
        return "goto";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();
        String[] tokens = argument.trim().split("\\s+");

        if (argument.isBlank() || tokens.length != 2) {
            connection.send(new Usage("goto <x> <y>"));
            return;
        }

        Optional<Double> x = parseDouble(tokens[0]);
        Optional<Double> y = parseDouble(tokens[1]);
        if (x.isEmpty() || y.isEmpty()) {
            connection.send(new Usage("goto <x> <y>"));
            return;
        }

        Position target = new Position(x.get(), y.get());
        Optional<List<Position>> path = GridPathfinder.findPath(character.getPosition(), target,
                character.getCurrentZone().getCollisionGrid());

        if (path.isEmpty()) {
            connection.send(new NoPathToDestination(target.x(), target.y()));
            return;
        }

        List<Position> waypoints = path.get();
        movementEngine.startMovement(waypoints, character);
        if (waypoints.isEmpty()) {
            connection.send(new ViewAround(character));
        } else {
            connection.send(new MovementStarted(target.x(), target.y()));
        }
    }

    private Optional<Double> parseDouble(String token) {
        try {
            return Optional.of(Double.parseDouble(token));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
