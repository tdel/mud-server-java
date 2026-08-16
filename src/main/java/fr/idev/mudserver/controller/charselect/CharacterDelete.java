package fr.idev.mudserver.controller.charselect;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.world.WorldInstance;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.charselect.CharacterCurrentlyInGame;
import fr.idev.mudserver.network.message.charselect.CharacterDeleted;
import fr.idev.mudserver.network.message.charselect.NoCharacterNamed;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;

@Component
public class CharacterDelete implements ControllerHandler {

    private static final Logger log = LoggerFactory.getLogger(CharacterDelete.class);

    private final CharacterDao characterDao;
    private final AccountDao accountDao;
    private final CharSelectStatus charSelectStatus;

    public CharacterDelete(CharacterDao characterDao, AccountDao accountDao, CharSelectStatus charSelectStatus) {
        this.characterDao = characterDao;
        this.accountDao = accountDao;
        this.charSelectStatus = charSelectStatus;
    }

    @Override
    public String name() {
        return "character-delete";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.CHARSELECT);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        String name = argument.trim();

        if (name.isEmpty()) {
            connection.send(new Usage("character-delete <name>"));
            return;
        }

        Account account = connection.account();
        WorldInstance instance = connection.worldInstance();

        Optional<CharacterInstance> character = characterDao.findByAccountAndWorldInstanceAndName(account, instance,
                name);
        if (character.isEmpty()) {
            connection.send(new NoCharacterNamed(name));
            charSelectStatus.show(connection, account, instance);
            return;
        }

        UUID characterId = character.get().getId();

        if (instance.isCharacterInGame(characterId)) {
            connection.send(new CharacterCurrentlyInGame(name));
            charSelectStatus.show(connection, account, instance);
            return;
        }

        if (characterId.equals(account.getCurrentCharacterId())) {
            accountDao.updateCurrentCharacter(account.getId(), null);
        }

        characterDao.deleteById(characterId);
        log.info("character.deleted character={} account={}", name, account.getLogin());

        connection.send(new CharacterDeleted(name));
        charSelectStatus.show(connection, account, instance);
    }
}
