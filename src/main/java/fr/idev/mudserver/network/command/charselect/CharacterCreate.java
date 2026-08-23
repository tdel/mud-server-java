package fr.idev.mudserver.network.command.charselect;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.network.CommandHandler;
import fr.idev.mudserver.network.command.ingame.Look;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.world.WorldInstance;
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.game.WorldInstanceService;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.charselect.CharacterCreated;
import fr.idev.mudserver.network.message.charselect.ChooseClass;
import fr.idev.mudserver.network.message.charselect.ChooseGender;
import fr.idev.mudserver.network.message.charselect.ChooseRace;
import fr.idev.mudserver.network.message.charselect.InvalidClass;
import fr.idev.mudserver.network.message.charselect.InvalidGender;
import fr.idev.mudserver.network.message.charselect.CharacterNameTaken;
import fr.idev.mudserver.network.message.charselect.InvalidRace;
import fr.idev.mudserver.network.message.charselect.NowPlaying;
import fr.idev.mudserver.network.message.ingame.GamePlayerStats;
import fr.idev.mudserver.network.message.ingame.ZoneMap;

@Component
public class CharacterCreate implements CommandHandler {

    private final WorldInstanceService worldInstanceService;
    private final CharSelectStatus charSelectStatus;
    private final Look lookAction;

    public CharacterCreate(WorldInstanceService worldInstanceService, CharSelectStatus charSelectStatus,
            Look lookAction) {
        this.worldInstanceService = worldInstanceService;
        this.charSelectStatus = charSelectStatus;
        this.lookAction = lookAction;
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

            promptRace(connection, account, instance, name, gender);
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

    private void promptRace(Connection connection, Account account, WorldInstance instance, String name,
            Gender gender) {
        Map<Race, Map<Attribute, Integer>> bonusesByRace = new LinkedHashMap<>();
        for (Race race : Race.values()) {
            bonusesByRace.put(race, race.attributeScoreBonuses());
        }

        connection.requestBlocking(new ChooseRace(bonusesByRace), line -> {
            Race race = parseRace(line);

            if (race == null) {
                connection.send(new InvalidRace(line.trim()));
                promptRace(connection, account, instance, name, gender);
                return;
            }

            promptClass(connection, account, instance, name, gender, race);
        });
    }

    private Race parseRace(String input) {
        String normalized = input.strip().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        try {
            return Race.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void promptClass(Connection connection, Account account, WorldInstance instance, String name, Gender gender,
            Race race) {
        Map<CharacterClass, Integer> hitDiceByClass = new LinkedHashMap<>();
        Map<CharacterClass, Attribute> primaryAbilityByClass = new LinkedHashMap<>();
        for (CharacterClass characterClass : CharacterClass.values()) {
            hitDiceByClass.put(characterClass, characterClass.hitDie());
            primaryAbilityByClass.put(characterClass, characterClass.primaryAbility());
        }

        connection.requestBlocking(new ChooseClass(hitDiceByClass, primaryAbilityByClass), line -> {
            CharacterClass characterClass = parseClass(line);

            if (characterClass == null) {
                connection.send(new InvalidClass(line.trim()));
                promptClass(connection, account, instance, name, gender, race);
                return;
            }

            createCharacter(connection, account, instance, name, gender, race, characterClass);
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
            Gender gender, Race race, CharacterClass characterClass) {
        CharacterInstance character = instance.createCharacter(account, name, gender, race, characterClass);

        connection.send(new CharacterCreated(name));
        connection.send(new GamePlayerStats(character));

        connection.attachCharacter(character);
        worldInstanceService.enterGame(character);

        connection.send(new NowPlaying(character.getName()));
        connection.send(new ZoneMap(character.getCurrentZone()));
        lookAction.onReceive(connection, "");
    }
}
