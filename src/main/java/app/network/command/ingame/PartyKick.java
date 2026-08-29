package app.network.command.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import app.domain.Party;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.instance.CharacterInstance;
import app.network.CommandHandler;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.ingame.CannotKickSelf;
import app.network.message.ingame.KickedFromParty;
import app.network.message.ingame.NoSuchPartyMember;
import app.network.message.ingame.NoTargetSelected;
import app.network.message.ingame.NotPartyLeader;
import app.network.message.ingame.PartyMemberKicked;
import app.network.message.ingame.TargetNotFound;

@Component
public class PartyKick implements CommandHandler {

    @Override
    public String name() {
        return "party-kick";
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
            connection.send(new CannotKickSelf());
            return;
        }
        if (!(selected instanceof CharacterInstance target)) {
            connection.send(new TargetNotFound(selected.getId().toString()));
            return;
        }

        Party party = character.getParty();
        if (party == null || !party.isLeader(character)) {
            connection.send(new NotPartyLeader());
            return;
        }
        if (!party.isMember(target)) {
            connection.send(new NoSuchPartyMember(target.getName()));
            return;
        }

        party.removeAndNotify(target);
        connection.send(new PartyMemberKicked(target.getId(), target.getName()));
        target.send(new KickedFromParty());
    }
}
