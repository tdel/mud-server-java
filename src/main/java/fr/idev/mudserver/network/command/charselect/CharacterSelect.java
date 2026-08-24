package fr.idev.mudserver.network.command.charselect;

import java.util.Optional;
import java.util.Set;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.network.CommandHandler;
import fr.idev.mudserver.network.command.ingame.Look;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.game.WorldInstanceService;
import fr.idev.mudserver.game.engine.SpellLearningEngine;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.charselect.NoCharacterNamed;
import fr.idev.mudserver.network.message.charselect.NowPlaying;
import fr.idev.mudserver.network.message.ingame.ZoneMap;
import fr.idev.mudserver.persistence.listener.ItemPersistenceListener;

@Component
public class CharacterSelect implements CommandHandler {

    private final WorldInstanceService worldInstanceService;
    private final ItemPersistenceListener itemService;
    private final CharSelectStatus charSelectStatus;
    private final Look lookAction;
    private final SpellLearningEngine spellLearningEngine;

    public CharacterSelect(WorldInstanceService worldInstanceService, ItemPersistenceListener itemService,
            CharSelectStatus charSelectStatus, Look lookAction, SpellLearningEngine spellLearningEngine) {
        this.worldInstanceService = worldInstanceService;
        this.itemService = itemService;
        this.charSelectStatus = charSelectStatus;
        this.lookAction = lookAction;
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
        connection.send(new ZoneMap(loadedChar.getCurrentZone()));
        lookAction.onReceive(connection, "");
    }
}
