package app.network.command.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import app.domain.Party;
import app.domain.PendingPartyInvite;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.instance.CharacterInstance;
import app.network.CommandHandler;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.ingame.AlreadyInParty;
import app.network.message.ingame.CannotInviteSelf;
import app.network.message.ingame.NoTargetSelected;
import app.network.message.ingame.NotPartyLeader;
import app.network.message.ingame.PartyInviteReceived;
import app.network.message.ingame.PartyInviteSent;
import app.network.message.ingame.TargetNotFound;

@Component
public class PartyInvite implements CommandHandler {

    @Override
    public String name() {
        return "party-invite";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();
        AbstractCharacter selected = character.getCombat().getTarget();

        if (selected == null) {
            connection.send(new NoTargetSelected());
            return;
        }
        if (selected == character) {
            connection.send(new CannotInviteSelf());
            return;
        }
        if (!(selected instanceof CharacterInstance target)
                || !connection.worldInstance().isCharacterInGame(target.getId())) {
            connection.send(new TargetNotFound(selected.getId().toString()));
            return;
        }

        Party myParty = character.getParty();
        if (myParty != null && !myParty.isLeader(character)) {
            connection.send(new NotPartyLeader());
            return;
        }
        if (target.getParty() != null) {
            connection.send(new AlreadyInParty(target.getName()));
            return;
        }

        Party party = myParty != null ? myParty : new Party(character);
        target.setPendingInvite(new PendingPartyInvite(party, character, System.currentTimeMillis()));

        connection.send(new PartyInviteSent(target.getId(), target.getName()));
        target.send(new PartyInviteReceived(character.getId(), character.getName()));
    }
}
