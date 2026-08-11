package fr.idev.mudserver.controller.ingame;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.CheckOutcome;

@Component
public class Save implements ControllerHandler {

    private static final String USAGE = "save <attribute> <dc>";

    @Override
    public String name() {
        return "save";
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
        Attribute attribute = parseAttribute(String.join(" ", Arrays.copyOfRange(tokens, 0, tokens.length - 1)));

        if (dc == null || attribute == null) {
            connection.send(new Usage(USAGE));
            return;
        }

        GamePlayer character = connection.character();
        connection.send(new CheckOutcome(character.save(attribute, dc)));
    }

    private Attribute parseAttribute(String input) {
        String normalized = input.strip().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        try {
            return Attribute.valueOf(normalized.toUpperCase(Locale.ROOT));
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
