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
import app.network.message.charselect.ChooseClass;
import app.network.message.charselect.ChooseGender;
import app.network.message.charselect.InvalidClass;
import app.network.message.charselect.InvalidGender;
import app.network.message.charselect.CharacterNameTaken;
import app.network.message.charselect.NowPlaying;
import app.network.message.ingame.GamePlayerStats;
import app.network.message.ingame.MapEnter;
import app.network.message.ingame.MapView;

@Component
public class CharacterCreate implements CommandHandler {

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
        String name = argument.trim();

        if (name.isEmpty()) {
            connection.send(new Usage("character-create <name>"));
            return;
        }

        Account account = connection.account();
        WorldInstance instance = connection.worldInstance();

        if (worldInstanceService.findCharacterByName(account, name).isPresent()) {
            connection.send(new CharacterNameTaken(name));
            charSelectStatus.show(connection, account);
            return;
        }

        promptGender(connection, account, instance, name);
    }

    private void promptGender(Connection connection, Account account, WorldInstance instance, String name) {
        connection.requestBlocking(new ChooseGender(), line -> {
            Gender gender = parseGender(line);

            if (gender == null) {
                connection.send(new InvalidGender(line.trim()));
                promptGender(connection, account, instance, name);
                return;
            }

            promptClass(connection, account, instance, name, gender);
        });
    }

    private Gender parseGender(String input) {
        String normalized = input.strip().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        try {
            return Gender.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void promptClass(Connection connection, Account account, WorldInstance instance, String name,
            Gender gender) {
        connection.requestBlocking(new ChooseClass(), line -> {
            CharacterClass characterClass = parseClass(line);

            if (characterClass == null) {
                connection.send(new InvalidClass(line.trim()));
                promptClass(connection, account, instance, name, gender);
                return;
            }

            createCharacter(connection, account, instance, name, gender, characterClass);
        });
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
