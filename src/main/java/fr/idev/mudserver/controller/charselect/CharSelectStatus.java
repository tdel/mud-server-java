package fr.idev.mudserver.controller.charselect;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.WorldInstance;
import fr.idev.mudserver.domain.WorldTemplate;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.game.WorldTemplateService;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.message.charselect.ExistingCharacterInWorld;
import fr.idev.mudserver.network.message.charselect.NoCharacterInWorld;
import fr.idev.mudserver.persistence.CharacterDao;

/**
 * Remplace le "relist" que {@code CharacterList}/{@code characters-list}
 * faisait avant sa suppression (voir {@code multi-world.md} Phase C) : au plus
 * un personnage par {@code (account_id, world_instance_id)}, donc un vrai
 * listing n'a plus de sens — un simple statut suffit, affiché après chaque
 * transition/action en CHARSELECT ({@code WorldEnter},
 * {@code CharacterCreate}/{@code CharacterDelete} en cas de refus ou de
 * succès).
 */
@Component
public class CharSelectStatus {

    private final CharacterDao characterDao;
    private final WorldTemplateService worldTemplateService;

    public CharSelectStatus(CharacterDao characterDao, WorldTemplateService worldTemplateService) {
        this.characterDao = characterDao;
        this.worldTemplateService = worldTemplateService;
    }

    public void show(Connection connection, Account account, WorldInstance instance) {
        String worldName = worldName(instance.getWorldTemplateId());
        Optional<GamePlayer> character = characterDao.findByAccountIdAndWorldInstanceId(account.getId(),
                instance.getId());

        if (character.isEmpty()) {
            connection.send(new NoCharacterInWorld(worldName));
            return;
        }

        GamePlayer existing = character.get();
        connection.send(new ExistingCharacterInWorld(worldName, existing.getName(), existing.getCharacterClass(),
                existing.getLevel()));
    }

    private String worldName(UUID worldTemplateId) {
        return worldTemplateService.findById(worldTemplateId).map(WorldTemplate::getName).orElse("this world");
    }
}
