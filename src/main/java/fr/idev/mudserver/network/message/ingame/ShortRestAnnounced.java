package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record ShortRestAnnounced(String initiatorName) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write(Ansi.player(initiatorName) + " calls for a short rest. Everyone catches their breath...\n");
    }
}
