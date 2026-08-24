package fr.idev.mudserver.network.command.ingame;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.map.HexPathfinder;
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
            connection.send(new Usage("goto <q> <r>"));
            return;
        }

        Optional<Integer> q = parseInt(tokens[0]);
        Optional<Integer> r = parseInt(tokens[1]);
        if (q.isEmpty() || r.isEmpty()) {
            connection.send(new Usage("goto <q> <r>"));
            return;
        }

        HexCoordinate target = new HexCoordinate(q.get(), r.get());
        Optional<List<HexCoordinate>> path = HexPathfinder.findPath(character.getPosition(), target,
                character.getCurrentZone()::isWalkable);

        if (path.isEmpty()) {
            connection.send(new NoPathToDestination(target.q(), target.r()));
            return;
        }

        List<HexCoordinate> steps = path.get();
        movementEngine.startMovement(steps, character);
        if (steps.isEmpty()) {
            connection.send(new ViewAround(character));
        } else {
            connection.send(new MovementStarted(target.q(), target.r()));
        }
    }

    private Optional<Integer> parseInt(String token) {
        try {
            return Optional.of(Integer.parseInt(token));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
