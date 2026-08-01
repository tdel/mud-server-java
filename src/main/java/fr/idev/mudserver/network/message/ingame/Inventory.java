package fr.idev.mudserver.network.message.ingame;

import java.util.List;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record Inventory(List<String> itemNames) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write(itemNames.isEmpty()
                ? "You aren't carrying anything.\n"
                : "You are carrying: " + String.join(", ", itemNames) + "\n");
    }
}
