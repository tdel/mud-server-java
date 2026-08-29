package app.network.command.charselect;

import java.util.Optional;
import java.util.Set;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import app.network.CommandHandler;
import app.domain.Account;
import app.domain.actor.instance.CharacterInstance;
import app.game.WorldInstanceService;
import app.game.engine.SpellLearningEngine;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.Usage;
import app.network.message.charselect.NoCharacterNamed;
import app.network.message.charselect.NowPlaying;
import app.network.message.ingame.MapEnter;
import app.network.message.ingame.MapView;
import app.persistence.listener.ItemPersistenceListener;

@Component
public class CharacterSelect implements CommandHandler {

    private final WorldInstanceService worldInstanceService;
    private final ItemPersistenceListener itemService;
    private final CharSelectStatus charSelectStatus;
    private final SpellLearningEngine spellLearningEngine;

    public CharacterSelect(WorldInstanceService worldInstanceService, ItemPersistenceListener itemService,
            CharSelectStatus charSelectStatus, SpellLearningEngine spellLearningEngine) {
        this.worldInstanceService = worldInstanceService;
        this.itemService = itemService;
        this.charSelectStatus = charSelectStatus;
        this.spellLearningEngine = spellLearningEngine;
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
        spellLearningEngine.reconcile(loadedChar);
        loadedChar.getInventory().replaceItems(itemService.loadInventory(loadedChar));
        loadedChar.getWorldInstance().loadPlayer(loadedChar);
        MDC.put("character", loadedChar.getName());

        connection.send(new NowPlaying(loadedChar.getName()));
        connection.send(new MapView(loadedChar.getCurrentMap()));
        connection.send(new MapEnter(loadedChar));
    }
}
