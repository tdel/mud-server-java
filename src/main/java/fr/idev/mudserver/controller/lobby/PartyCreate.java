package fr.idev.mudserver.controller.lobby;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.game.PartyService;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.lobby.AlreadyInParty;
import fr.idev.mudserver.network.message.lobby.PartyCreated;
import fr.idev.mudserver.network.message.lobby.PartyMembersList;

@Component
public class PartyCreate implements ControllerHandler {

    private final PartyService partyService;

    public PartyCreate(PartyService partyService) {
        this.partyService = partyService;
    }

    @Override
    public String name() {
        return "party-create";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.LOBBY);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        Account account = connection.account();

        if (partyService.partyOf(account.getId()).isPresent()) {
            connection.send(new AlreadyInParty());
            return;
        }

        partyService.createParty(account.getId());
        connection.send(new PartyCreated());
        connection.send(new PartyMembersList(List.of(account.getLogin())));
    }
}
