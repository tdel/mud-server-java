package fr.idev.mudserver.controller.charselect;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.controller.ingame.Look;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.WorldInstance;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.CharacterSelectionWorld;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.charselect.NowPlaying;
import fr.idev.mudserver.persistence.CharacterDao;

/**
 * Pas d'argument {@code <name>} : au plus un personnage par
 * {@code (account_id, world_instance_id)} (voir {@code multi-world.md} Phase
 * C), donc rien à désambiguïser — tout argument fourni est ignoré.
 */
@Component
public class CharacterSelect implements ControllerHandler {

    private final CharacterDao characterDao;
    private final AuthWorld authWorld;
    private final CharacterSelectionWorld characterSelectionWorld;
    private final CharSelectStatus charSelectStatus;
    private final Look lookAction;

    public CharacterSelect(CharacterDao characterDao, AuthWorld authWorld,
            CharacterSelectionWorld characterSelectionWorld, CharSelectStatus charSelectStatus, Look lookAction) {
        this.characterDao = characterDao;
        this.authWorld = authWorld;
        this.characterSelectionWorld = characterSelectionWorld;
        this.charSelectStatus = charSelectStatus;
        this.lookAction = lookAction;
    }

    @Override
    public String name() {
        return "character-select";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.CHARSELECT);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        Account account = authWorld.account(connection);
        WorldInstance instance = characterSelectionWorld.worldInstance(connection);

        Optional<GamePlayer> character = characterDao.findByAccountIdAndWorldInstanceId(account.getId(),
                instance.getId());
        if (character.isEmpty()) {
            charSelectStatus.show(connection, account, instance);
            return;
        }

        authWorld.moveToGameWorld(connection, character.get());

        connection.send(new NowPlaying(character.get().getName()));
        lookAction.onReceive(connection, "");
    }
}
