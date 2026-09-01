package app.network.command.charselect;

import java.util.List;

import org.springframework.stereotype.Component;

import app.domain.Account;
import app.domain.actor.instance.CharacterInstance;
import app.game.WorldInstanceService;
import app.network.Connection;
import app.network.message.charselect.CharacterList;
import app.network.message.charselect.NoCharacters;

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

        connection.send(new CharacterList(characters.stream()
                .map(character -> new CharacterList.Entry(character.getName(),
                        character.getAppearanceSystem().getRace(), character.getClassSystem().getCharacterClass(),
                        character.getLevel()))
                .toList()));
    }
}
