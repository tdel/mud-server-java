package fr.idev.mudserver.network.command.charselect;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.network.CommandHandler;
import fr.idev.mudserver.network.command.ingame.Look;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.game.WorldInstanceService;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.charselect.NoCharacterNamed;
import fr.idev.mudserver.network.message.charselect.NowPlaying;

@Component
public class CharacterSelect implements CommandHandler {

    private final WorldInstanceService worldInstanceService;
    private final CharSelectStatus charSelectStatus;
    private final Look lookAction;

    public CharacterSelect(WorldInstanceService worldInstanceService, CharSelectStatus charSelectStatus,
            Look lookAction) {
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
        String name = argument.trim();

        if (name.isEmpty()) {
            connection.send(new Usage("character-select <name>"));
            return;
        }

        Account account = connection.account();

        Optional<CharacterInstance> character = worldInstanceService.findCharacterByName(account, name);
        if (character.isEmpty()) {
            connection.send(new NoCharacterNamed(name));
            charSelectStatus.show(connection, account);
            return;
        }

        CharacterInstance loadedChar = character.get();
        connection.attachCharacter(loadedChar);
        worldInstanceService.enterGame(loadedChar);

        connection.send(new NowPlaying(loadedChar.getName()));
        lookAction.onReceive(connection, "");
    }
}
