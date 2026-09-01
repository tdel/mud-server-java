package app.network.command.ingame;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import app.domain.actor.Subclass;
import app.domain.actor.instance.CharacterInstance;
import app.network.CommandHandler;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.Usage;
import app.network.message.ingame.InvalidSubclass;
import app.network.message.ingame.NoPendingSubclassChoice;

@Component
public class ChooseSubclass implements CommandHandler {

    @Override
    public String name() {
        return "choose-subclass";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        String input = argument.trim();

        if (input.isEmpty()) {
            connection.send(new Usage("choose-subclass <name>"));
            return;
        }

        CharacterInstance character = connection.character();
        Integer tier = character.getClassSystem().getPendingSubclassTier();

        if (tier == null) {
            connection.send(new NoPendingSubclassChoice());
            return;
        }

        List<Subclass> options = Subclass.availableAt(character.getClassSystem().getCharacterClass(), tier);
        Subclass subclass = parseSubclass(input);

        if (subclass == null || !options.contains(subclass)) {
            connection.send(new InvalidSubclass(input, options));
            return;
        }

        character.getClassSystem().chooseSubclass(subclass);
    }

    private Subclass parseSubclass(String input) {
        String normalized = input.strip().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        try {
            return Subclass.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
