package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record PlayerRespawned(String roomName) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("You collapse, defeated... Your vision fades to black. You awaken at the " + roomName
                + ", wounds mended.\n");
    }
}
