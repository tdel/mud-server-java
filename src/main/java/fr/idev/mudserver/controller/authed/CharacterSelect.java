package fr.idev.mudserver.controller.authed;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.controller.ingame.Look;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.GamePlayer;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.authed.NoCharacterNamed;
import fr.idev.mudserver.network.message.authed.NowPlaying;
import fr.idev.mudserver.persistence.CharacterDao;

@Component
public class CharacterSelect implements ControllerHandler {

    private final CharacterDao characterDao;
    private final AuthWorld authWorld;
    private final CharacterList characterListAction;
    private final Look lookAction;

    public CharacterSelect(CharacterDao characterDao, AuthWorld authWorld, CharacterList characterListAction,
            Look lookAction) {
        this.characterDao = characterDao;
        this.authWorld = authWorld;
        this.characterListAction = characterListAction;
        this.lookAction = lookAction;
    }

    @Override
    public String name() {
        return "character-select";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.AUTHED);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        String name = argument.trim();

        if (name.isEmpty()) {
            connection.send(new Usage("character-select <name>"));
            characterListAction.onReceive(connection, "");
            return;
        }

        Account account = authWorld.account(connection);

        Optional<GamePlayer> character = characterDao.findByAccountIdAndName(account.getId(), name);
        if (character.isEmpty()) {
            connection.send(new NoCharacterNamed(name));
            characterListAction.onReceive(connection, "");
            return;
        }

        authWorld.moveToGameWorld(connection, character.get());

        connection.send(new NowPlaying(character.get().getName()));
        lookAction.onReceive(connection, "");
    }
}
