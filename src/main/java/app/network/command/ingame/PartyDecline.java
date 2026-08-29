package app.network.command.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import app.domain.PendingPartyInvite;
import app.domain.actor.instance.CharacterInstance;
import app.network.CommandHandler;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.ingame.NoPendingInvite;
import app.network.message.ingame.PartyInviteDeclined;

@Component
public class PartyDecline implements CommandHandler {

    @Override
    public String name() {
        return "party-decline";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();
        PendingPartyInvite invite = character.getPendingInvite();

        if (invite == null) {
            connection.send(new NoPendingInvite());
            return;
        }

        character.setPendingInvite(null);
        connection.send(new PartyInviteDeclined(invite.inviter().getName()));
        invite.inviter().send(new PartyInviteDeclined(character.getName()));
    }
}
