package fr.idev.mudserver.network.message.charselect;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record NoStartingRoom() implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("No starting room is configured. Contact the administrator (room-create).\n");
    }
}
