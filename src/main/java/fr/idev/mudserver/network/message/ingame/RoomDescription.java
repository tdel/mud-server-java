package fr.idev.mudserver.network.message.ingame;

import java.util.List;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record RoomDescription(
        String roomName,
        String description,
        List<String> exitNames,
        List<String> characterNames,
        List<String> itemNames
) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write(String.format(
                "== %s ==\n%s\n\nExits: %s\nCharacters here: %s\nItems: %s\n",
                roomName,
                description,
                exitNames.isEmpty() ? "none." : String.join(", ", exitNames),
                characterNames.isEmpty() ? "no one else." : String.join(", ", characterNames),
                itemNames.isEmpty() ? "none." : String.join(", ", itemNames)
        ));
    }
}
