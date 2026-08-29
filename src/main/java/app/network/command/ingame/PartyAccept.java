package app.network.command.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import app.domain.Party;
import app.domain.PendingPartyInvite;
import app.domain.actor.instance.CharacterInstance;
import app.network.CommandHandler;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.ingame.NoPendingInvite;
import app.network.message.ingame.PartyJoined;
import app.network.message.ingame.PartyMemberJoined;

@Component
public class PartyAccept implements CommandHandler {

    @Override
    public String name() {
        return "party-accept";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();
        PendingPartyInvite invite = character.getPendingInvite();

        if (invite == null || invite.party().isEmpty()) {
            character.setPendingInvite(null);
            connection.send(new NoPendingInvite());
            return;
        }

        character.setPendingInvite(null);
        Party party = invite.party();
        party.addMember(character);

        connection.send(new PartyJoined(party.getLeader().getId(), party.getLeader().getName(), party.size()));
        party.broadcast(new PartyMemberJoined(character.getId(), character.getName()), character);
    }
}
