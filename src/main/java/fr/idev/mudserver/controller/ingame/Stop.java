package fr.idev.mudserver.controller.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.system.MovementSystem;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.ingame.MovementStopped;

@Component
public class Stop implements ControllerHandler {

    private final MovementSystem movementSystem;

    public Stop(MovementSystem movementSystem) {
        this.movementSystem = movementSystem;
    }

    @Override
    public String name() {
        return "stop";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();
        movementSystem.stopMovement(character);
        connection.send(new MovementStopped());
    }
}
