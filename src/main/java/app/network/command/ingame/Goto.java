package app.network.command.ingame;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import app.domain.actor.instance.CharacterInstance;
import app.domain.map.GridPathfinder;
import app.domain.map.Position;
import app.game.engine.MovementEngine;
import app.network.CommandHandler;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.Usage;
import app.network.message.ingame.CharacterMovementStarted;
import app.network.message.ingame.MovementStarted;
import app.network.message.ingame.NoPathToDestination;
import app.network.message.ingame.ViewAround;

@Component
public class Goto implements CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(Goto.class);

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
            log.debug("movement.no_path character={} target=({},{})", character.getId(), target.x(), target.y());
            connection.send(new NoPathToDestination(target.x(), target.y()));
            return;
        }

        List<Position> waypoints = path.get();
        log.info("movement.requested character={} target=({},{}) waypoints={}", character.getId(), target.x(),
                target.y(), waypoints.size());
        movementEngine.startMovement(waypoints, character);
        if (waypoints.isEmpty()) {
            connection.send(new ViewAround(character));
        } else {
            connection.send(new MovementStarted(target.x(), target.y()));
            character.getCurrentZone().broadcast(
                    new CharacterMovementStarted(character.getName(), target.x(), target.y()), character);
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
