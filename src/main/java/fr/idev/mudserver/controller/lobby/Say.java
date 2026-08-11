package fr.idev.mudserver.controller.lobby;

import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.ingame.SayNothing;
import fr.idev.mudserver.network.message.ingame.YouSaid;
import fr.idev.mudserver.network.message.lobby.Chat;

@Component("lobbySay")
public class Say implements ControllerHandler {

    private final AuthWorld authWorld;

    public Say(AuthWorld authWorld) {
        this.authWorld = authWorld;
    }

    @Override
    public String name() {
        return "say";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.LOBBY);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        Account account = authWorld.account(connection);
        String message = argument.trim();

        if (message.isEmpty()) {
            connection.send(new SayNothing());
            return;
        }

        authWorld.broadcastToLobby(new Chat(account.getLogin(), message), connection);

        connection.send(new YouSaid(message));
    }
}
