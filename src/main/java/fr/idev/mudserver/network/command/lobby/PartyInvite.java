package fr.idev.mudserver.network.command.lobby;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.network.CommandHandler;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.Party;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.PartyService;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.lobby.AlreadyInAnotherParty;
import fr.idev.mudserver.network.message.lobby.CannotInviteSelf;
import fr.idev.mudserver.network.message.lobby.NotPartyLeader;
import fr.idev.mudserver.network.message.lobby.PartyInviteReceived;
import fr.idev.mudserver.network.message.lobby.PartyInviteSent;
import fr.idev.mudserver.network.message.lobby.PlayerNotOnline;

@Component
public class PartyInvite implements CommandHandler {

    private final AuthWorld authWorld;
    private final PartyService partyService;

    public PartyInvite(AuthWorld authWorld, PartyService partyService) {
        this.authWorld = authWorld;
        this.partyService = partyService;
    }

    @Override
    public String name() {
        return "party-invite";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.LOBBY);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        String targetLogin = argument.trim();
        if (targetLogin.isEmpty()) {
            connection.send(new Usage("party-invite <login>"));
            return;
        }

        Account account = connection.account();

        if (targetLogin.equalsIgnoreCase(account.getLogin())) {
            connection.send(new CannotInviteSelf());
            return;
        }

        Optional<Party> partyOpt = partyService.partyOf(account.getId());
        if (partyOpt.isEmpty() || !partyOpt.get().isLeader(account.getId())) {
            connection.send(new NotPartyLeader());
            return;
        }
        Party party = partyOpt.get();

        Optional<Connection> targetConnection = authWorld.findConnectionByLogin(targetLogin);
        if (targetConnection.isEmpty() || targetConnection.get().state() != ConnectionState.LOBBY) {
            connection.send(new PlayerNotOnline(targetLogin));
            return;
        }
        Account targetAccount = targetConnection.get().account();

        if (partyService.partyOf(targetAccount.getId()).isPresent()) {
            connection.send(new AlreadyInAnotherParty(targetLogin));
            return;
        }

        partyService.invite(party, targetAccount.getId());
        connection.send(new PartyInviteSent(targetLogin));
        targetConnection.get().send(new PartyInviteReceived(account.getLogin()));
    }
}
