package fr.idev.mudserver.network.command.lobby;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.network.CommandHandler;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.Party;
import fr.idev.mudserver.domain.PartyMember;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.PartyService;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.lobby.NewPartyLeader;
import fr.idev.mudserver.network.message.lobby.NotInParty;
import fr.idev.mudserver.network.message.lobby.PartyLeft;
import fr.idev.mudserver.network.message.lobby.PartyMemberLeft;
import fr.idev.mudserver.persistence.AccountDao;

@Component
public class PartyLeave implements CommandHandler {

    private final AuthWorld authWorld;
    private final PartyService partyService;
    private final AccountDao accountDao;

    public PartyLeave(AuthWorld authWorld, PartyService partyService, AccountDao accountDao) {
        this.authWorld = authWorld;
        this.partyService = partyService;
        this.accountDao = accountDao;
    }

    @Override
    public String name() {
        return "party-leave";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.LOBBY);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        Account account = connection.account();

        Optional<Party> partyOpt = partyService.partyOf(account.getId());
        if (partyOpt.isEmpty()) {
            connection.send(new NotInParty());
            return;
        }
        Party party = partyOpt.get();

        boolean wasLeader = party.isLeader(account.getId());
        partyService.leave(party, account.getId());
        connection.send(new PartyLeft());

        for (PartyMember member : party.getMembers()) {
            authWorld.findConnectionByAccountId(member.accountId())
                    .ifPresent(memberConnection -> memberConnection.send(new PartyMemberLeft(account.getLogin())));
        }

        if (wasLeader && !party.getMembers().isEmpty()) {
            String newLeaderLogin = accountDao.findById(party.getLeaderAccountId()).map(Account::getLogin).orElse("?");
            for (PartyMember member : party.getMembers()) {
                authWorld.findConnectionByAccountId(member.accountId())
                        .ifPresent(memberConnection -> memberConnection.send(new NewPartyLeader(newLeaderLogin)));
            }
        }
    }
}
