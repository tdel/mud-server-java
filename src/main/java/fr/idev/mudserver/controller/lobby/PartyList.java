package fr.idev.mudserver.controller.lobby;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.Party;
import fr.idev.mudserver.domain.PartyMember;
import fr.idev.mudserver.game.PartyService;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.lobby.NotInParty;
import fr.idev.mudserver.network.message.lobby.PartyMembersList;
import fr.idev.mudserver.persistence.AccountDao;

@Component
public class PartyList implements ControllerHandler {

    private final PartyService partyService;
    private final AccountDao accountDao;

    public PartyList(PartyService partyService, AccountDao accountDao) {
        this.partyService = partyService;
        this.accountDao = accountDao;
    }

    @Override
    public String name() {
        return "party-list";
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

        connection.send(new PartyMembersList(loginsOf(partyOpt.get())));
    }

    private List<String> loginsOf(Party party) {
        return party.getMembers().stream().map(PartyMember::accountId)
                .map(accountId -> accountDao.findById(accountId).map(Account::getLogin).orElse("?")).toList();
    }
}
