package app.network.command.ingame;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import app.domain.Party;
import app.network.CommandHandler;
import app.domain.actor.instance.PlayerInstance;
import app.game.WorldInstanceService;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.ingame.CannotWhisperSelf;
import app.network.message.ingame.Chat;
import app.network.message.ingame.NotInParty;
import app.network.message.ingame.PartyChat;
import app.network.message.ingame.SayNothing;
import app.network.message.ingame.Whisper;
import app.network.message.ingame.WhisperTargetNotFound;
import app.network.message.ingame.YouSaid;

@Component
public class Say implements CommandHandler {

    private final WorldInstanceService worldInstanceService;

    public Say(WorldInstanceService worldInstanceService) {
        this.worldInstanceService = worldInstanceService;
    }

    @Override
    public String name() {
        return "say";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        PlayerInstance character = connection.character();
        String raw = argument.trim();

        if (raw.isEmpty()) {
            connection.send(new SayNothing());
            return;
        }

        if (raw.charAt(0) == '#') {
            handleWhisper(connection, character, raw.substring(1));
            return;
        }

        if (raw.charAt(0) == '%') {
            handleParty(connection, character, raw.substring(1).trim());
            return;
        }

        character.broadcast(new Chat(character.getName(), raw), character);
        connection.send(new YouSaid(raw));
    }

    private void handleWhisper(Connection connection, PlayerInstance character, String rest) {
        String trimmed = rest.trim();
        int separator = trimmed.indexOf(' ');
        String targetName = separator == -1 ? trimmed : trimmed.substring(0, separator);
        String message = separator == -1 ? "" : trimmed.substring(separator + 1).trim();

        if (targetName.isEmpty() || message.isEmpty()) {
            connection.send(new SayNothing());
            return;
        }

        if (targetName.equalsIgnoreCase(character.getName())) {
            connection.send(new CannotWhisperSelf());
            return;
        }

        Optional<PlayerInstance> target = connection.worldInstance().onlineCharacters().stream()
                .filter(candidate -> candidate.getName().equalsIgnoreCase(targetName)).findFirst();

        if (target.isEmpty()) {
            connection.send(new WhisperTargetNotFound(targetName));
            return;
        }

        Whisper whisper = new Whisper(character.getName(), target.get().getName(), message);
        connection.send(whisper);
        target.get().send(whisper);
    }

    private void handleParty(Connection connection, PlayerInstance character, String message) {
        if (message.isEmpty()) {
            connection.send(new SayNothing());
            return;
        }

        Party party = character.getParty();
        if (party == null) {
            connection.send(new NotInParty());
            return;
        }

        party.broadcast(new PartyChat(character.getName(), message), null);
    }
}
