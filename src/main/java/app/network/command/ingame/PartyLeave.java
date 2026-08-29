package app.network.command.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import app.domain.Party;
import app.domain.actor.instance.CharacterInstance;
import app.network.CommandHandler;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.ingame.NotInParty;
import app.network.message.ingame.PartyLeft;

@Component
public class PartyLeave implements CommandHandler {

    @Override
    public String name() {
        return "party-leave";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();
        Party party = character.getParty();

        if (party == null) {
            connection.send(new NotInParty());
            return;
        }

        party.removeAndNotify(character);
        connection.send(new PartyLeft());
    }
}
