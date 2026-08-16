package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.domain.item.Rarity;
import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record ItemDiscarded(String name, Rarity rarity) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("You discard the " + Ansi.item(name, rarity) + ".\n");
    }
}
