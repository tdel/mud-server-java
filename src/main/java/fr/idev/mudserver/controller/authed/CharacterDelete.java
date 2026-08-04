package fr.idev.mudserver.controller.authed;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.GamePlayer;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.authed.CharacterCurrentlyInGame;
import fr.idev.mudserver.network.message.authed.CharacterDeleted;
import fr.idev.mudserver.network.message.authed.NoCharacterNamed;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;

@Component
public class CharacterDelete implements ControllerHandler {

    private final CharacterDao characterDao;
    private final AccountDao accountDao;
    private final CharacterList characterListAction;
    private final AuthWorld authWorld;
    private final GameWorld gameWorld;

    public CharacterDelete(CharacterDao characterDao, AccountDao accountDao, CharacterList characterListAction,
            AuthWorld authWorld, GameWorld gameWorld) {
        this.characterDao = characterDao;
        this.accountDao = accountDao;
        this.characterListAction = characterListAction;
        this.authWorld = authWorld;
        this.gameWorld = gameWorld;
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
    public void onReceive(Connection connection, String argument) {
        String name = argument.trim();

        if (name.isEmpty()) {
            connection.send(new Usage("character-delete <name>"));
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

        UUID characterId = character.get().getId();

        if (gameWorld.isCharacterInGame(characterId)) {
            connection.send(new CharacterCurrentlyInGame(name));
            characterListAction.onReceive(connection, "");
            return;
        }

        if (characterId.equals(account.getCurrentCharacterId())) {
            accountDao.updateCurrentCharacter(account.getId(), null);
        }

        characterDao.deleteById(characterId);

        connection.send(new CharacterDeleted(name));
        characterListAction.onReceive(connection, "");
    }
}
