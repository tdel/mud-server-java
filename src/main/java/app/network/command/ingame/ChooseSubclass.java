package app.network.command.ingame;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import app.domain.actor.Subclass;
import app.domain.actor.instance.PlayerInstance;
import app.network.CommandHandler;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.Usage;
import app.network.message.ingame.ClassOptionView;
import app.network.message.ingame.InvalidSubclass;
import app.network.message.ingame.NoPendingSubclassChoice;

// "choose-subclass <nom>" : nom d'enum (DARK_AVENGER) ou nom L2 ("Dark
// Avenger", "Human Wizard"). Succès : SubclassChosen, puis NewSkillsAvailable
// si la nouvelle profession ouvre des compétences (SkillLearningEngine).
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
    public boolean requiresAlive() {
        return true;
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        String input = argument.trim();

        if (input.isEmpty()) {
            connection.send(new Usage("choose-subclass <name>"));
            return;
        }

        PlayerInstance character = connection.character();
        Integer tier = character.getClassSystem().getPendingSubclassTier();

        if (tier == null) {
            connection.send(new NoPendingSubclassChoice(character.getClassSystem().nextChangeLevel()));
            return;
        }

        List<Subclass> options = character.getClassSystem().nextOptions();
        Subclass subclass = parseSubclass(input);

        if (subclass == null || !options.contains(subclass)) {
            connection.send(new InvalidSubclass(input, ClassOptionView.listOf(options)));
            return;
        }

        character.getClassSystem().chooseSubclass(subclass);
    }

    static Subclass parseSubclass(String input) {
        String normalized = input.strip().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (Subclass subclass : Subclass.values()) {
            if (subclass.name().equals(normalized)
                    || subclass.label().toUpperCase(Locale.ROOT).replace(' ', '_').equals(normalized)) {
                return subclass;
            }
        }
        return null;
    }
}
