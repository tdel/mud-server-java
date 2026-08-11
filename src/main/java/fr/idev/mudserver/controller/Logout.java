package fr.idev.mudserver.controller;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.WorldInstanceService;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.LoggedOut;
import fr.idev.mudserver.network.message.charselect.StoppedPlaying;
import fr.idev.mudserver.network.message.lobby.BackInLobby;

/**
 * Utilisable depuis les états "ingame", "charselect" et "lobby". Depuis
 * "ingame", se déloguer lâche le personnage et ramène directement au Lobby
 * (plus d'étape intermédiaire par "charselect" — pour rejouer, il faut refaire
 * {@code world-enter}). Depuis "charselect", on revient aussi au Lobby. Depuis
 * "lobby", se déloguer repasse entièrement à "connected".
 */
@Component
public class Logout implements ControllerHandler {

    private static final Logger log = LoggerFactory.getLogger(Logout.class);

    private final AuthWorld authWorld;
    private final WorldInstanceService worldInstanceService;

    public Logout(AuthWorld authWorld, WorldInstanceService worldInstanceService) {
        this.authWorld = authWorld;
        this.worldInstanceService = worldInstanceService;
    }

    @Override
    public String name() {
        return "logout";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.LOBBY, ConnectionState.CHARSELECT, ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        if (connection.state() == ConnectionState.INGAME) {
            GamePlayer character = connection.character();

            worldInstanceService.exitGame(connection);
            worldInstanceService.exitCharSelect(connection);

            connection.send(new StoppedPlaying(character.getName()));
            connection.send(new BackInLobby());
            return;
        }

        if (connection.state() == ConnectionState.CHARSELECT) {
            worldInstanceService.exitCharSelect(connection);
            connection.send(new BackInLobby());
            return;
        }

        if (connection.state() == ConnectionState.LOBBY) {
            Account account = connection.account();
            authWorld.exitWorld(connection);
            log.info("auth.logged_out account={}", account.getLogin());
            connection.send(new LoggedOut());
            return;
        }

        throw new IllegalStateException("not handled!");
    }
}
