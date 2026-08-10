package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record NotEnoughProvisions(int totalValue, int threshold) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write(Ansi.error("Not enough provisions selected (" + totalValue + "/" + threshold
                + ") — the long rest doesn't happen. Nothing was consumed.") + "\n");
    }
}
