package fr.idev.mudserver.controller.lobby;

import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.PartyService;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.lobby.AlreadyInParty;
import fr.idev.mudserver.network.message.lobby.PartyCreated;

@Component
public class PartyCreate implements ControllerHandler {

    private final AuthWorld authWorld;
    private final PartyService partyService;

    public PartyCreate(AuthWorld authWorld, PartyService partyService) {
        this.authWorld = authWorld;
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
        Account account = authWorld.account(connection);

        if (partyService.partyOf(account.getId()).isPresent()) {
            connection.send(new AlreadyInParty());
            return;
        }

        partyService.createParty(account.getId());
        connection.send(new PartyCreated());
    }
}
