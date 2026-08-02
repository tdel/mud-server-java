package fr.idev.mudserver.controller;

import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.authed.CharacterList;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.LoggedOut;
import fr.idev.mudserver.network.message.authed.StoppedPlaying;

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

    public Logout(GameWorld gameWorld, AuthWorld authWorld, CharacterList characterListAction) {
        this.gameWorld = gameWorld;
        this.authWorld = authWorld;
        this.characterListAction = characterListAction;
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
    public void onReceive(Connection session, String argument) {
        if (session.state() == ConnectionState.INGAME) {
            String characterName = session.player().character().name();

            gameWorld.exitWorld(session.player());
            authWorld.enterWorld(session);

            session.send(new StoppedPlaying(characterName));
            characterListAction.onReceive(session, "");
            return;
        }

        if (session.state() == ConnectionState.AUTHED) {
            authWorld.exitWorld(session);
            session.send(new LoggedOut());
            return;
        }

        throw new IllegalStateException("not handled!");
    }
}
