package fr.idev.mudserver.controller.authed;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.GamePlayer;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.persistence.CharacterDao;

@Component
public class CharacterList implements ControllerHandler {

    private final CharacterDao characterDao;
    private final AuthWorld authWorld;

    public CharacterList(CharacterDao characterDao, AuthWorld authWorld) {
        this.characterDao = characterDao;
        this.authWorld = authWorld;
    }

    @Override
    public String name() {
        return "characters-list";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.AUTHED);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        List<GamePlayer> characters = characterDao.findByAccountId(authWorld.account(connection).getId());
        List<String> names = characters.stream().map(GamePlayer::getName).toList();
        connection.send(new fr.idev.mudserver.network.message.authed.CharacterList(names));
    }
}
