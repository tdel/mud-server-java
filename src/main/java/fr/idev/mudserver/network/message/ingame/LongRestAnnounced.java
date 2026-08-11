package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record LongRestAnnounced(String initiatorName) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write(Ansi.player(initiatorName)
                + " shares provisions and calls for a long rest. Everyone settles in for the night...\n");
    }
}
