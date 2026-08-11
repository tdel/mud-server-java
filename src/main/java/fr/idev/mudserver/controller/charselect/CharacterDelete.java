package fr.idev.mudserver.controller.charselect;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.WorldInstance;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.CharacterSelectionWorld;
import fr.idev.mudserver.game.GameWorld;
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
    private final AuthWorld authWorld;
    private final CharacterSelectionWorld characterSelectionWorld;
    private final GameWorld gameWorld;
    private final CharSelectStatus charSelectStatus;

    public CharacterDelete(CharacterDao characterDao, AccountDao accountDao, AuthWorld authWorld,
            CharacterSelectionWorld characterSelectionWorld, GameWorld gameWorld, CharSelectStatus charSelectStatus) {
        this.characterDao = characterDao;
        this.accountDao = accountDao;
        this.authWorld = authWorld;
        this.characterSelectionWorld = characterSelectionWorld;
        this.gameWorld = gameWorld;
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

        Account account = authWorld.account(connection);
        WorldInstance instance = characterSelectionWorld.worldInstance(connection);

        Optional<GamePlayer> character = characterDao.findByAccountIdAndName(account.getId(), instance.getId(), name);
        if (character.isEmpty()) {
            connection.send(new NoCharacterNamed(name));
            charSelectStatus.show(connection, account, instance);
            return;
        }

        UUID characterId = character.get().getId();

        if (gameWorld.isCharacterInGame(characterId)) {
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
