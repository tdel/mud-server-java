package app.network.command.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import app.domain.Party;
import app.domain.actor.instance.CharacterInstance;
import app.network.CommandHandler;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.ingame.NotPartyLeader;
import app.network.message.ingame.PartyDisbanded;

@Component
public class PartyDisband implements CommandHandler {

    @Override
    public String name() {
        return "party-disband";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();
        Party party = character.getParty();

        if (party == null || !party.isLeader(character)) {
            connection.send(new NotPartyLeader());
            return;
        }

        party.broadcast(new PartyDisbanded(), null);
        party.disband();
    }
}
