package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record GoldSpent(int amount) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("You spend " + Ansi.gold(amount) + " gold.\n");
    }
}
