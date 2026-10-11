package app.network.command.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import app.game.engine.ResurrectionEngine;
import app.network.CommandHandler;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.Error;

// "resurrect-accept" : accepte la résurrection proposée par un prêtre
// (ResurrectionOffered).
@Component
public class ResurrectAccept implements CommandHandler {

    private final ResurrectionEngine resurrectionEngine;

    public ResurrectAccept(ResurrectionEngine resurrectionEngine) {
        this.resurrectionEngine = resurrectionEngine;
    }

    @Override
    public String name() {
        return "resurrect-accept";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        if (!resurrectionEngine.accept(connection.character())) {
            connection.send(new Error("Aucune résurrection en attente."));
        }
    }
}
