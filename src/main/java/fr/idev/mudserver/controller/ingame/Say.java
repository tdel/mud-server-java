package fr.idev.mudserver.controller.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.ingame.Chat;
import fr.idev.mudserver.network.message.ingame.SayNothing;
import fr.idev.mudserver.network.message.ingame.YouSaid;

@Component
public class Say implements ControllerHandler {

    @Override
    public String name() {
        return "say";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();
        String message = argument.trim();

        if (message.isEmpty()) {
            connection.send(new SayNothing());
            return;
        }

        character.getWorldInstance().broadcast(new Chat(character.getName(), message), character);
        connection.send(new YouSaid(message));
    }
}
