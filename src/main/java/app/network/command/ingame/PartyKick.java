package app.network.command.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import app.domain.Party;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.instance.PlayerInstance;
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

/**
 * "party-kick" exclut la cible sélectionnée, ou le membre dont l'UUID est donné
 * en argument ("party-kick &lt;uuid&gt;", menu de la fenêtre de groupe) : un
 * coéquipier sur une autre carte n'est pas sélectionnable (Select ne résout que
 * les occupants de la carte courante). Tout le groupe restant reçoit
 * PartyMemberKicked (et non PartyMemberLeft) pour distinguer l'exclusion d'un
 * départ volontaire.
 */
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
        PlayerInstance character = connection.character();
        Party party = character.getPartySystem().getParty();

        if (argument != null && !argument.isBlank()) {
            if (party == null || !party.isLeader(character)) {
                connection.send(new NotPartyLeader());
                return;
            }
            String memberId = argument.strip();
            PlayerInstance member = party.getMembers().stream()
                    .filter(candidate -> candidate.getId().toString().equals(memberId)).findFirst().orElse(null);
            if (member == null) {
                connection.send(new NoSuchPartyMember(memberId));
                return;
            }
            if (member == character) {
                connection.send(new CannotKickSelf());
                return;
            }
            kick(party, member);
            return;
        }

        AbstractCharacter selected = character.getCombatSystem().getTarget();
        if (selected == null) {
            connection.send(new NoTargetSelected());
            return;
        }
        if (selected == character) {
            connection.send(new CannotKickSelf());
            return;
        }
        if (!(selected instanceof PlayerInstance target)) {
            connection.send(new TargetNotFound(selected.getId().toString()));
            return;
        }
        if (party == null || !party.isLeader(character)) {
            connection.send(new NotPartyLeader());
            return;
        }
        if (!party.isMember(target)) {
            connection.send(new NoSuchPartyMember(target.getName()));
            return;
        }
        kick(party, target);
    }

    private void kick(Party party, PlayerInstance target) {
        party.remove(target);
        party.broadcast(new PartyMemberKicked(target.getId(), target.getName()), null);
        target.send(new KickedFromParty());
        party.disbandIfAlone();
    }
}
