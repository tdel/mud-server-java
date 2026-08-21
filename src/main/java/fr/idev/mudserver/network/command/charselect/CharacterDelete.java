package fr.idev.mudserver.network.command.charselect;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.network.CommandHandler;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.world.WorldInstance;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.game.WorldInstanceService;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.charselect.CharacterCurrentlyInGame;
import fr.idev.mudserver.network.message.charselect.CharacterDeleted;
import fr.idev.mudserver.network.message.charselect.NoCharacterNamed;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;

@Component
public class CharacterDelete implements CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(CharacterDelete.class);

    private final WorldInstanceService worldInstanceService;
    private final CharacterDao characterDao;
    private final AccountDao accountDao;
    private final CharSelectStatus charSelectStatus;

    public CharacterDelete(WorldInstanceService worldInstanceService, CharacterDao characterDao, AccountDao accountDao,
            CharSelectStatus charSelectStatus) {
        this.worldInstanceService = worldInstanceService;
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

        Optional<CharacterInstance> character = worldInstanceService.findCharacterByName(account, name);
        if (character.isEmpty()) {
            connection.send(new NoCharacterNamed(name));
            charSelectStatus.show(connection, account);
            return;
        }

        UUID characterId = character.get().getId();

        if (instance.isCharacterInGame(characterId)) {
            connection.send(new CharacterCurrentlyInGame(name));
            charSelectStatus.show(connection, account);
            return;
        }

        if (characterId.equals(account.getCurrentCharacterId())) {
            accountDao.updateCurrentCharacter(account.getId(), null);
        }

        characterDao.deleteById(characterId);
        log.info("character.deleted character={} account={}", name, account.getLogin());

        connection.send(new CharacterDeleted(name));
        charSelectStatus.show(connection, account);
    }
}
