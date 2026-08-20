package fr.idev.mudserver.network.command.charselect;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.world.WorldInstance;
import fr.idev.mudserver.domain.world.WorldTemplateSummary;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.game.WorldInstanceService;
import fr.idev.mudserver.game.catalog.WorldTemplateCatalog;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.message.charselect.ExistingCharacterInWorld;
import fr.idev.mudserver.network.message.charselect.NoCharacterInWorld;

@Component
public class CharSelectStatus {

    private final WorldInstanceService worldInstanceService;
    private final WorldTemplateCatalog worldTemplateService;

    public CharSelectStatus(WorldInstanceService worldInstanceService, WorldTemplateCatalog worldTemplateService) {
        this.worldInstanceService = worldInstanceService;
        this.worldTemplateService = worldTemplateService;
    }

    public void show(Connection connection, Account account, WorldInstance instance) {
        String worldName = worldName(instance.getWorldTemplateId());
        Optional<CharacterInstance> character = worldInstanceService.findCharacterFor(account, instance);

        if (character.isEmpty()) {
            connection.send(new NoCharacterInWorld(worldName));
            return;
        }

        CharacterInstance existing = character.get();
        connection.send(new ExistingCharacterInWorld(worldName, existing.getName(), existing.getCharacterClass(),
                existing.getLevel()));
    }

    private String worldName(UUID worldTemplateId) {
        return worldTemplateService.findSummaryById(worldTemplateId).map(WorldTemplateSummary::name)
                .orElse("this world");
    }
}
