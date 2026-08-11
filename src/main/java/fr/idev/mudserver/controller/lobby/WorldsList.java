package fr.idev.mudserver.controller.lobby;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.WorldInstance;
import fr.idev.mudserver.domain.WorldTemplate;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.game.WorldInstanceService;
import fr.idev.mudserver.game.WorldTemplateService;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.persistence.WorldInstanceDao;

@Component
public class WorldsList implements ControllerHandler {

    private final WorldTemplateService worldTemplateService;
    private final WorldInstanceDao worldInstanceDao;
    private final WorldInstanceService worldInstanceService;

    public WorldsList(WorldTemplateService worldTemplateService, WorldInstanceDao worldInstanceDao,
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
        for (WorldTemplate template : worldTemplateService.allTemplates()) {
            // getOrMaterialize, pas l'instance brute renvoyée par le DAO : GamePlayer
            // exige désormais une RoomInstance résolue dès sa construction (voir
            // CharacterDao.toDomain), qui n'existe que sur une WorldInstance
            // matérialisée (son roomInstances est sinon vide).
            Optional<GamePlayer> existingCharacter = worldInstanceDao
                    .findByAccountIdAndWorldTemplateId(account.getId(), template.getId())
                    .map(instance -> worldInstanceService.getOrMaterialize(instance.getId()))
                    .flatMap(instance -> worldInstanceService.findCharacterFor(account, instance));

            entries.add(new fr.idev.mudserver.network.message.lobby.WorldsList.Entry(template.getShortName(),
                    template.getName(), template.getDescription(), template.getMinPlayers(), template.getMaxPlayers(),
                    existingCharacter.map(GamePlayer::getName).orElse(null),
                    existingCharacter.map(GamePlayer::getCharacterClass).orElse(null),
                    existingCharacter.map(GamePlayer::getLevel).orElse(null)));
        }

        connection.send(new fr.idev.mudserver.network.message.lobby.WorldsList(entries));
    }
}
