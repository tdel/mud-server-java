package app.network.command.ingame;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import app.network.CommandHandler;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.instance.CharacterInstance;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.ingame.TargetDeselected;
import app.network.message.ingame.TargetNotFound;
import app.network.message.ingame.TargetSelected;

@Component
public class Select implements CommandHandler {

    @Override
    public String name() {
        return "select";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();
        String name = argument.trim();

        if (name.isEmpty()) {
            character.getCombat().setTarget(null);
            connection.send(new TargetDeselected());
            return;
        }

        // Monstre, joueur ou PNJ : la sélection sert aussi bien à cibler un sort/une
        // attaque (monstre, joueur) qu'à préparer une interaction (PNJ, commerce/quête).
        Optional<AbstractCharacter> target = character.getCurrentZone().findOccupantByName(name);
        if (target.isEmpty()) {
            connection.send(new TargetNotFound(name));
            return;
        }

        character.getCombat().setTarget(target.get());
        connection.send(new TargetSelected(target.get().getName()));
    }
}
