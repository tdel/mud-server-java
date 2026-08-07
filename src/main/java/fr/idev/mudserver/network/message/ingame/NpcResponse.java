package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record NpcResponse(String npcName, String response) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write(npcName + " says: " + response + "\n");
    }
}
