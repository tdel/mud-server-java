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
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.lobby.CannotKickSelf;
import fr.idev.mudserver.network.message.lobby.NoSuchPartyMember;
import fr.idev.mudserver.network.message.lobby.NotPartyLeader;
import fr.idev.mudserver.network.message.lobby.PartyKicked;
import fr.idev.mudserver.network.message.lobby.PartyMemberKicked;
import fr.idev.mudserver.network.message.lobby.PartyMemberLeft;
import fr.idev.mudserver.persistence.AccountDao;

@Component
public class PartyKick implements ControllerHandler {

    private final AuthWorld authWorld;
    private final PartyService partyService;
    private final AccountDao accountDao;

    public PartyKick(AuthWorld authWorld, PartyService partyService, AccountDao accountDao) {
        this.authWorld = authWorld;
        this.partyService = partyService;
        this.accountDao = accountDao;
    }

    @Override
    public String name() {
        return "party-kick";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.LOBBY);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        String targetLogin = argument.trim();
        if (targetLogin.isEmpty()) {
            connection.send(new Usage("party-kick <login>"));
            return;
        }

        Account account = authWorld.account(connection);

        if (targetLogin.equalsIgnoreCase(account.getLogin())) {
            connection.send(new CannotKickSelf());
            return;
        }

        Optional<Party> partyOpt = partyService.partyOf(account.getId());
        if (partyOpt.isEmpty() || !partyOpt.get().isLeader(account.getId())) {
            connection.send(new NotPartyLeader());
            return;
        }
        Party party = partyOpt.get();

        Optional<Account> targetAccount = accountDao.findByLogin(targetLogin);
        if (targetAccount.isEmpty() || !party.isMember(targetAccount.get().getId())) {
            connection.send(new NoSuchPartyMember(targetLogin));
            return;
        }

        partyService.kick(party, targetAccount.get().getId());
        connection.send(new PartyKicked(targetLogin));

        authWorld.findConnectionByAccountId(targetAccount.get().getId())
                .ifPresent(targetConnection -> targetConnection.send(new PartyMemberKicked()));

        for (PartyMember member : party.getMembers()) {
            if (member.accountId().equals(account.getId())) {
                continue;
            }
            authWorld.findConnectionByAccountId(member.accountId())
                    .ifPresent(memberConnection -> memberConnection.send(new PartyMemberLeft(targetLogin)));
        }
    }
}
