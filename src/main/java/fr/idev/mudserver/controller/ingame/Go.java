package fr.idev.mudserver.controller.ingame;

import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.HexDirection;
import fr.idev.mudserver.domain.actor.GameCharacter.MovementOutcome;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.MovementBlockedByBounds;
import fr.idev.mudserver.network.message.ingame.MovementBlockedByOccupant;
import fr.idev.mudserver.network.message.ingame.NoSuchDirection;

/**
 * Ne téléporte plus d'une {@code Room} à l'autre : déplace le personnage de 1 à
 * N cases (N borné par sa vitesse) dans la grille hexagonale de sa room
 * actuelle — voir
 * {@link fr.idev.mudserver.domain.actor.GameCharacter#moveToCell}. Atterrir sur
 * une case-portail fait automatiquement traverser vers la room liée, à la case
 * cible correspondante.
 */
@Component
public class Go implements ControllerHandler {

    private static final Logger log = LoggerFactory.getLogger(Go.class);

    private static final int DEFAULT_STEP_COUNT = 1;

    private final GameWorld gameWorld;
    private final Look lookAction;

    public Go(GameWorld gameWorld, Look lookAction) {
        this.gameWorld = gameWorld;
        this.lookAction = lookAction;
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
        GamePlayer character = gameWorld.character(connection);
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

        MovementOutcome outcome = character.moveToCell(direction.get(), requestedCells);

        if (outcome.cellsMoved() == 0) {
            String reason = outcome.blockedByOccupant() ? "occupant" : "bounds";
            log.debug("room.move_blocked reason={} character={}", reason, character.getName());
            connection.send(
                    outcome.blockedByOccupant() ? new MovementBlockedByOccupant() : new MovementBlockedByBounds());
            return;
        }

        lookAction.onReceive(connection, "");
    }

    private int parsePositiveInt(String token) {
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
