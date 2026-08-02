package fr.idev.mudserver.controller.authed;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.authed.CharacterDeleted;
import fr.idev.mudserver.network.message.authed.NoCharacterNamed;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;

@Component
public class CharacterDelete implements ControllerHandler {

    private final CharacterDao characterDao;
    private final AccountDao accountDao;
    private final CharacterList characterListAction;

    public CharacterDelete(CharacterDao characterDao, AccountDao accountDao, CharacterList characterListAction) {
        this.characterDao = characterDao;
        this.accountDao = accountDao;
        this.characterListAction = characterListAction;
    }

    @Override
    public String name() {
        return "character-delete";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.AUTHED);
    }

    @Override
    public void onReceive(Connection session, String argument) {
        String name = argument.trim();

        if (name.isEmpty()) {
            session.send(new Usage("character-delete <name>"));
            characterListAction.onReceive(session, "");
            return;
        }

        Account account = session.account();

        Optional<Character> character = characterDao.findByAccountIdAndName(account.id(), name);
        if (character.isEmpty()) {
            session.send(new NoCharacterNamed(name));
            characterListAction.onReceive(session, "");
            return;
        }

        UUID characterId = character.get().id();
        if (characterId.equals(account.currentCharacterId())) {
            accountDao.updateCurrentCharacter(account.id(), null);
        }

        characterDao.deleteById(characterId);

        session.send(new CharacterDeleted(name));
        characterListAction.onReceive(session, "");
    }
}
