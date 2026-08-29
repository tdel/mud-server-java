package app.network.command.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import app.domain.Party;
import app.domain.actor.instance.CharacterInstance;
import app.network.CommandHandler;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.ingame.InvalidLootMode;
import app.network.message.ingame.NotInParty;
import app.network.message.ingame.NotPartyLeader;
import app.network.message.ingame.PartyLootModeChanged;

@Component
public class PartyLoot implements CommandHandler {

    @Override
    public String name() {
        return "party-loot";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();
        Party party = character.getParty();

        if (party == null) {
            connection.send(new NotInParty());
            return;
        }
        if (!party.isLeader(character)) {
            connection.send(new NotPartyLeader());
            return;
        }

        Party.LootMode lootMode = switch (argument == null ? "" : argument.trim().toLowerCase()) {
            case "random" -> Party.LootMode.RANDOM;
            case "byturn" -> Party.LootMode.ROUND_ROBIN;
            default -> null;
        };

        if (lootMode == null) {
            connection.send(new InvalidLootMode(argument));
            return;
        }

        party.setLootMode(lootMode);
        party.broadcast(new PartyLootModeChanged(lootMode.name()), null);
    }
}
