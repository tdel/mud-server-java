package fr.idev.mudserver.controller.authed;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.persistence.CharacterDao;

@Component
public class CharacterList implements ControllerHandler {

    private final CharacterDao characterDao;

    public CharacterList(CharacterDao characterDao) {
        this.characterDao = characterDao;
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
    public void onReceive(Connection session, String argument) {
        List<Character> characters = characterDao.findByAccountId(session.account().id());
        List<String> names = characters.stream().map(Character::name).toList();
        session.send(new fr.idev.mudserver.network.message.authed.CharacterList(names));
    }
}
