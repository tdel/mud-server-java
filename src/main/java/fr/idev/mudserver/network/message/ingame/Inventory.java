package fr.idev.mudserver.network.message.ingame;

import java.util.List;
import java.util.stream.Collectors;

import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record Inventory(List<String> itemNames, int gold) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write(itemNames.isEmpty()
                ? "You aren't carrying anything.\n"
                : "You are carrying: " + itemNames.stream().map(Ansi::item).collect(Collectors.joining(", ")) + "\n");
        output.write("Gold: " + Ansi.gold(gold) + " gp\n");
    }
}
