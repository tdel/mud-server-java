package fr.idev.mudserver.network.message.ingame;

import java.util.List;
import java.util.stream.Collectors;

import fr.idev.mudserver.domain.item.Rarity;
import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record Inventory(List<Entry> items, int gold) implements OutputTelnetMessage {

    public record Entry(String name, Rarity rarity) {
    }

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write(items.isEmpty()
                ? "You aren't carrying anything.\n"
                : "You are carrying: " + items.stream().map(item -> Ansi.item(item.name(), item.rarity()))
                        .collect(Collectors.joining(", ")) + "\n");
        output.write("Gold: " + Ansi.gold(gold) + " gp\n");
    }
}
