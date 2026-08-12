package fr.idev.mudserver.controller.charselect;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.WorldInstance;
import fr.idev.mudserver.domain.WorldTemplateSummary;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.game.WorldInstanceService;
import fr.idev.mudserver.game.WorldTemplateService;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.message.charselect.ExistingCharacterInWorld;
import fr.idev.mudserver.network.message.charselect.NoCharacterInWorld;

@Component
public class CharSelectStatus {

    private final WorldInstanceService worldInstanceService;
    private final WorldTemplateService worldTemplateService;

    public CharSelectStatus(WorldInstanceService worldInstanceService, WorldTemplateService worldTemplateService) {
        this.worldInstanceService = worldInstanceService;
        this.worldTemplateService = worldTemplateService;
    }

    public void show(Connection connection, Account account, WorldInstance instance) {
        String worldName = worldName(instance.getWorldTemplateId());
        Optional<GamePlayer> character = worldInstanceService.findCharacterFor(account, instance);

        if (character.isEmpty()) {
            connection.send(new NoCharacterInWorld(worldName));
            return;
        }

        GamePlayer existing = character.get();
        connection.send(new ExistingCharacterInWorld(worldName, existing.getName(), existing.getCharacterClass(),
                existing.getLevel()));
    }

    private String worldName(UUID worldTemplateId) {
        return worldTemplateService.findSummaryById(worldTemplateId).map(WorldTemplateSummary::name)
                .orElse("this world");
    }
}
