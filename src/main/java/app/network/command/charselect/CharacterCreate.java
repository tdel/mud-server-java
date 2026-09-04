package app.network.command.charselect;

import java.util.Locale;
import java.util.Set;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import app.network.CommandHandler;
import app.domain.Account;
import app.domain.world.WorldInstance;
import app.domain.actor.instance.CharacterInstance;
import app.domain.actor.CharacterClass;
import app.domain.actor.Gender;
import app.domain.actor.Race;
import app.game.WorldInstanceService;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.Usage;
import app.network.message.charselect.CharacterCreated;
import app.network.message.charselect.InvalidClass;
import app.network.message.charselect.InvalidGender;
import app.network.message.charselect.CharacterNameTaken;
import app.network.message.charselect.NowPlaying;
import app.network.message.ingame.GamePlayerStats;
import app.network.message.ingame.MapEnter;
import app.network.message.ingame.MapView;

@Component
public class CharacterCreate implements CommandHandler {

    private static final String USAGE = "character-create <name>|<gender>|<classe>";

    private final WorldInstanceService worldInstanceService;
    private final CharSelectStatus charSelectStatus;

    public CharacterCreate(WorldInstanceService worldInstanceService, CharSelectStatus charSelectStatus) {
        this.worldInstanceService = worldInstanceService;
        this.charSelectStatus = charSelectStatus;
    }

    @Override
    public String name() {
        return "character-create";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.CHARSELECT);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        String[] parts = argument.split("\\|", -1);
        if (parts.length != 3) {
            connection.send(new Usage(USAGE));
            return;
        }

        String name = parts[0].trim();
        if (name.isEmpty()) {
            connection.send(new Usage(USAGE));
            return;
        }

        Account account = connection.account();
        WorldInstance instance = connection.worldInstance();

        if (worldInstanceService.findCharacterByName(account, name).isPresent()) {
            connection.send(new CharacterNameTaken(name));
            charSelectStatus.show(connection, account);
            return;
        }

        Gender gender = parseGender(parts[1]);
        if (gender == null) {
            connection.send(new InvalidGender(parts[1].trim()));
            return;
        }

        CharacterClass characterClass = parseClass(parts[2]);
        if (characterClass == null) {
            connection.send(new InvalidClass(parts[2].trim()));
            return;
        }

        createCharacter(connection, account, instance, name, gender, characterClass);
    }

    private Gender parseGender(String input) {
        String normalized = input.strip().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        try {
            return Gender.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private CharacterClass parseClass(String input) {
        String normalized = input.strip().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        try {
            return CharacterClass.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void createCharacter(Connection connection, Account account, WorldInstance instance, String name,
            Gender gender, CharacterClass characterClass) {
        CharacterInstance character = instance.createCharacter(account, name, gender, Race.HUMAN, characterClass);

        connection.send(new CharacterCreated(name));
        connection.send(new GamePlayerStats(character));

        connection.attachCharacter(character);
        character.getWorldInstance().loadPlayer(character);
        MDC.put("character", character.getName());

        connection.send(new NowPlaying(character.getName()));
        connection.send(new MapView(character.getMotionSystem().getCurrentMap()));
        connection.send(new MapEnter(character));
    }
}
