package app.network.command.ingame;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import app.network.CommandHandler;
import app.domain.actor.instance.CharacterInstance;
import app.domain.actor.Skill;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.Usage;
import app.network.message.ingame.CheckOutcome;

@Component
public class Check implements CommandHandler {

    private static final String USAGE = "check <skill> <dc>";

    @Override
    public String name() {
        return "check";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        String[] tokens = argument.trim().split("\\s+");

        if (tokens.length < 2) {
            connection.send(new Usage(USAGE));
            return;
        }

        Integer dc = parseDc(tokens[tokens.length - 1]);
        Skill skill = parseSkill(String.join(" ", Arrays.copyOfRange(tokens, 0, tokens.length - 1)));

        if (dc == null || skill == null) {
            connection.send(new Usage(USAGE));
            return;
        }

        CharacterInstance character = connection.character();
        connection.send(new CheckOutcome(character.check(skill, dc)));
    }

    private Skill parseSkill(String input) {
        String normalized = input.strip().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        try {
            return Skill.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Integer parseDc(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
