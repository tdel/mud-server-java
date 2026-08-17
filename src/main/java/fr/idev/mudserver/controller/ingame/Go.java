package fr.idev.mudserver.controller.ingame;

import java.util.Optional;
import java.util.Set;

import fr.idev.mudserver.domain.actor.component.MovementComponent;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.map.HexDirection;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.system.MovementSystem;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.NoSuchDirection;

@Component
public class Go implements ControllerHandler {

    private static final int DEFAULT_STEP_COUNT = 1;
    private static final int MAX_STEP_COUNT = 20;

    private final MovementSystem movementSystem;

    public Go(MovementSystem movementSystem) {
        this.movementSystem = movementSystem;
    }

    @Override
    public String name() {
        return "go";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();
        String[] tokens = argument.trim().split("\\s+");

        if (argument.isBlank()) {
            connection.send(new Usage("go <direction> [count]"));
            return;
        }

        Optional<HexDirection> direction = HexDirection.fromToken(tokens[0]);
        if (direction.isEmpty()) {
            connection.send(new NoSuchDirection(tokens[0]));
            return;
        }

        int requestedCells = tokens.length > 1 ? parsePositiveInt(tokens[1]) : DEFAULT_STEP_COUNT;
        if (requestedCells <= 0) {
            connection.send(new Usage("go <direction> [count]"));
            return;
        }
        final int cellsCount = Math.min(requestedCells, MAX_STEP_COUNT);

        character.updateComponent(MovementComponent.class,
                current -> new MovementComponent(direction.get(), cellsCount, System.currentTimeMillis()));
    }

    private int parsePositiveInt(String token) {
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
