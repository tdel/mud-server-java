package fr.idev.mudserver.controller.lobby;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.world.WorldTemplateSummary;
import fr.idev.mudserver.domain.actor.component.AppearanceComponent;
import fr.idev.mudserver.domain.actor.component.IdentityComponent;
import fr.idev.mudserver.domain.actor.component.LevelingComponent;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.game.WorldInstanceService;
import fr.idev.mudserver.game.catalog.WorldTemplateCatalog;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.persistence.WorldInstanceDao;

@Component
public class WorldsList implements ControllerHandler {

    private final WorldTemplateCatalog worldTemplateService;
    private final WorldInstanceDao worldInstanceDao;
    private final WorldInstanceService worldInstanceService;

    public WorldsList(WorldTemplateCatalog worldTemplateService, WorldInstanceDao worldInstanceDao,
            WorldInstanceService worldInstanceService) {
        this.worldTemplateService = worldTemplateService;
        this.worldInstanceDao = worldInstanceDao;
        this.worldInstanceService = worldInstanceService;
    }

    @Override
    public String name() {
        return "worlds-list";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.LOBBY);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        Account account = connection.account();

        List<fr.idev.mudserver.network.message.lobby.WorldsList.Entry> entries = new ArrayList<>();
        for (WorldTemplateSummary template : worldTemplateService.allSummaries()) {
            // getOrMaterialize, pas l'instance brute renvoyée par le DAO : GamePlayer
            // exige désormais une RoomInstance résolue dès sa construction (voir
            // CharacterDao.toDomain), qui n'existe que sur une WorldInstance
            // matérialisée (son roomInstances est sinon vide).
            Optional<CharacterInstance> existingCharacter = worldInstanceDao
                    .findByAccountIdAndWorldTemplateId(account.getId(), template.id())
                    .map(instance -> worldInstanceService.getOrMaterialize(instance.getId()))
                    .flatMap(instance -> worldInstanceService.findCharacterFor(account, instance));

            entries.add(new fr.idev.mudserver.network.message.lobby.WorldsList.Entry(template.shortName(),
                    template.name(), template.description(), template.minPlayers(), template.maxPlayers(),
                    existingCharacter.map(character -> character.component(IdentityComponent.class).name).orElse(null),
                    existingCharacter.map(character -> character.component(AppearanceComponent.class).characterClass)
                            .orElse(null),
                    existingCharacter.map(character -> character.component(LevelingComponent.class).level)
                            .orElse(null)));
        }

        connection.send(new fr.idev.mudserver.network.message.lobby.WorldsList(entries));
    }
}
