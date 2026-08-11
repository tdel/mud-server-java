package fr.idev.mudserver.controller.lobby;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.Party;
import fr.idev.mudserver.domain.PartyMember;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.PartyService;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.lobby.NoPendingInvite;
import fr.idev.mudserver.network.message.lobby.PartyJoined;
import fr.idev.mudserver.network.message.lobby.PartyMemberJoined;
import fr.idev.mudserver.persistence.AccountDao;

@Component
public class PartyAccept implements ControllerHandler {

    private final AuthWorld authWorld;
    private final PartyService partyService;
    private final AccountDao accountDao;

    public PartyAccept(AuthWorld authWorld, PartyService partyService, AccountDao accountDao) {
        this.authWorld = authWorld;
        this.partyService = partyService;
        this.accountDao = accountDao;
    }

    @Override
    public String name() {
        return "party-accept";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.LOBBY);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        Account account = connection.account();

        Optional<Party> partyOpt = partyService.pendingInviteFor(account.getId());
        if (partyOpt.isEmpty()) {
            connection.send(new NoPendingInvite());
            return;
        }
        Party party = partyOpt.get();

        partyService.accept(party, account.getId());

        String leaderLogin = accountDao.findById(party.getLeaderAccountId()).map(Account::getLogin).orElse("?");
        connection.send(new PartyJoined(leaderLogin, party.getMembers().size()));

        for (PartyMember member : party.getMembers()) {
            if (member.accountId().equals(account.getId())) {
                continue;
            }
            authWorld.findConnectionByAccountId(member.accountId())
                    .ifPresent(memberConnection -> memberConnection.send(new PartyMemberJoined(account.getLogin())));
        }
    }
}
