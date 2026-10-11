package app.network.command.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import app.domain.actor.instance.PlayerInstance;
import app.game.engine.SummonEngine;
import app.network.CommandHandler;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.Error;

// "unsummon" : renvoie son serviteur invoqué.
@Component
public class Unsummon implements CommandHandler {

    private final SummonEngine summonEngine;

    public Unsummon(SummonEngine summonEngine) {
        this.summonEngine = summonEngine;
    }

    @Override
    public String name() {
        return "unsummon";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        PlayerInstance character = connection.character();
        if (character.getSummon() == null) {
            connection.send(new Error("Vous n'avez aucun serviteur invoqué."));
            return;
        }
        summonEngine.dismiss(character.getSummon(), "Vous renvoyez votre serviteur.");
    }
}
