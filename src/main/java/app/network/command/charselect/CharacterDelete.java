package app.network.command.charselect;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import app.network.CommandHandler;
import app.domain.Account;
import app.domain.world.WorldInstance;
import app.domain.actor.instance.CharacterInstance;
import app.game.WorldInstanceService;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.Usage;
import app.network.message.charselect.CharacterCurrentlyInGame;
import app.network.message.charselect.CharacterDeleted;
import app.network.message.charselect.NoCharacterNamed;
import app.persistence.CharacterDao;

@Component
public class CharacterDelete implements CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(CharacterDelete.class);

    private final WorldInstanceService worldInstanceService;
    private final CharacterDao characterDao;
    private final CharSelectStatus charSelectStatus;

    public CharacterDelete(WorldInstanceService worldInstanceService, CharacterDao characterDao,
            CharSelectStatus charSelectStatus) {
        this.worldInstanceService = worldInstanceService;
        this.characterDao = characterDao;
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

        characterDao.deleteById(characterId);
        log.info("character.deleted character={} account={}", name, account.getLogin());

        connection.send(new CharacterDeleted(name));
        charSelectStatus.show(connection, account);
    }
}
