package fr.idev.mudserver.controller;

import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.authed.CharacterList;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.LoggedOut;
import fr.idev.mudserver.network.message.authed.StoppedPlaying;
import fr.idev.mudserver.persistence.AccountDao;

/**
 * Utilisable depuis les états "authed" et "ingame". Depuis "ingame", se
 * déloguer ne fait que lâcher le personnage et revenir à la sélection
 * ("authed"). Depuis "authed", se déloguer repasse entièrement à "connected".
 */
@Component
public class Logout implements ControllerHandler {

    private final GameWorld gameWorld;
    private final AuthWorld authWorld;
    private final CharacterList characterListAction;
    private final AccountDao accountDao;

    public Logout(GameWorld gameWorld, AuthWorld authWorld, CharacterList characterListAction, AccountDao accountDao) {
        this.gameWorld = gameWorld;
        this.authWorld = authWorld;
        this.characterListAction = characterListAction;
        this.accountDao = accountDao;
    }

    @Override
    public String name() {
        return "logout";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.AUTHED, ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        if (connection.state() == ConnectionState.INGAME) {
            Character character = gameWorld.character(connection);

            gameWorld.exitWorld(connection);
            Account account = accountDao.findById(character.getAccountId()).orElseThrow();
            authWorld.enterWorld(connection, account);

            connection.send(new StoppedPlaying(character.getName()));
            characterListAction.onReceive(connection, "");
            return;
        }

        if (connection.state() == ConnectionState.AUTHED) {
            authWorld.exitWorld(connection);
            connection.send(new LoggedOut());
            return;
        }

        throw new IllegalStateException("not handled!");
    }
}
