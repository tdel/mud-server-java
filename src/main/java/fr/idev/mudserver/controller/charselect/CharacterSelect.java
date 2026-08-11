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
import fr.idev.mudserver.game.WorldInstanceService;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.charselect.NowPlaying;

/**
 * Pas d'argument {@code <name>} : au plus un personnage par
 * {@code (account_id, world_instance_id)} (voir {@code multi-world.md} Phase
 * C), donc rien à désambiguïser — tout argument fourni est ignoré.
 */
@Component
public class CharacterSelect implements ControllerHandler {

    private final AuthWorld authWorld;
    private final WorldInstanceService worldInstanceService;
    private final CharSelectStatus charSelectStatus;
    private final Look lookAction;

    public CharacterSelect(AuthWorld authWorld, WorldInstanceService worldInstanceService,
            CharSelectStatus charSelectStatus, Look lookAction) {
        this.authWorld = authWorld;
        this.worldInstanceService = worldInstanceService;
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
        WorldInstance instance = worldInstanceService.worldInstanceOf(connection);

        Optional<GamePlayer> character = worldInstanceService.findCharacterFor(account, instance);
        if (character.isEmpty()) {
            charSelectStatus.show(connection, account, instance);
            return;
        }

        worldInstanceService.enterGame(connection, character.get());

        connection.send(new NowPlaying(character.get().getName()));
        lookAction.onReceive(connection, "");
    }
}
