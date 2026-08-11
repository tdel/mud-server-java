package fr.idev.mudserver.controller;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.WorldInstance;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.CharacterSelectionWorld;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.game.WorldInstanceService;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.LoggedOut;
import fr.idev.mudserver.network.message.charselect.StoppedPlaying;
import fr.idev.mudserver.network.message.lobby.BackInLobby;
import fr.idev.mudserver.persistence.AccountDao;

/**
 * Utilisable depuis les états "ingame", "charselect" et "lobby". Depuis
 * "ingame", se déloguer ne fait que lâcher le personnage et revenir à la
 * sélection de personnage de la même {@code WorldInstance} ("charselect").
 * Depuis "charselect", on revient au Lobby. Depuis "lobby", se déloguer repasse
 * entièrement à "connected".
 */
@Component
public class Logout implements ControllerHandler {

    private static final Logger log = LoggerFactory.getLogger(Logout.class);

    private final GameWorld gameWorld;
    private final AuthWorld authWorld;
    private final CharacterSelectionWorld characterSelectionWorld;
    private final WorldInstanceService worldInstanceService;
    private final AccountDao accountDao;

    public Logout(GameWorld gameWorld, AuthWorld authWorld, CharacterSelectionWorld characterSelectionWorld,
            WorldInstanceService worldInstanceService, AccountDao accountDao) {
        this.gameWorld = gameWorld;
        this.authWorld = authWorld;
        this.characterSelectionWorld = characterSelectionWorld;
        this.worldInstanceService = worldInstanceService;
        this.accountDao = accountDao;
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
            GamePlayer character = gameWorld.character(connection);

            gameWorld.exitWorld(connection);
            Account account = accountDao.findById(character.getAccountId()).orElseThrow();
            authWorld.enterWorld(connection, account);
            WorldInstance instance = worldInstanceService.getOrMaterialize(character.getWorldInstanceId());
            characterSelectionWorld.enterWorld(connection, instance);

            connection.send(new StoppedPlaying(character.getName()));
            return;
        }

        if (connection.state() == ConnectionState.CHARSELECT) {
            characterSelectionWorld.exitWorld(connection);
            connection.send(new BackInLobby());
            return;
        }

        if (connection.state() == ConnectionState.LOBBY) {
            Account account = authWorld.account(connection);
            authWorld.exitWorld(connection);
            log.info("auth.logged_out account={}", account.getLogin());
            connection.send(new LoggedOut());
            return;
        }

        throw new IllegalStateException("not handled!");
    }
}
