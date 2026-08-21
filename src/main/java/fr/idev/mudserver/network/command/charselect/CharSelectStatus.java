package fr.idev.mudserver.network.command.charselect;

import java.util.List;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.game.WorldInstanceService;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.message.charselect.CharacterList;
import fr.idev.mudserver.network.message.charselect.NoCharacters;

@Component
public class CharSelectStatus {

    private final WorldInstanceService worldInstanceService;

    public CharSelectStatus(WorldInstanceService worldInstanceService) {
        this.worldInstanceService = worldInstanceService;
    }

    public void show(Connection connection, Account account) {
        List<CharacterInstance> characters = worldInstanceService.findCharactersFor(account);

        if (characters.isEmpty()) {
            connection.send(new NoCharacters());
            return;
        }

        connection
                .send(new CharacterList(
                        characters
                                .stream().map(character -> new CharacterList.Entry(character.getName(),
                                        character.getRace(), character.getCharacterClass(), character.getLevel()))
                                .toList()));
    }
}
