package app.network.command;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Component;

import app.network.CommandHandler;
import app.network.Connection;
import app.network.ConnectionState;

// Réponse du client au Ping de heartbeat. Rien à faire ici : toute ligne reçue
// réarme déjà la détection d'inactivité de TcpJsonSessionHandler — la commande
// existe seulement pour ne pas répondre ActionNotFound.
@Component
public class Pong implements CommandHandler {

    @Override
    public String name() {
        return "pong";
    }

    @Override
    public Set<ConnectionState> states() {
        return EnumSet.allOf(ConnectionState.class);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
    }
}
