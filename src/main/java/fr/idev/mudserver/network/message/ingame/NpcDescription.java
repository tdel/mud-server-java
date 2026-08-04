package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.domain.GameNpc;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record NpcDescription(GameNpc npc) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("== " + npc.getName() + " ==\n");
    }
}
