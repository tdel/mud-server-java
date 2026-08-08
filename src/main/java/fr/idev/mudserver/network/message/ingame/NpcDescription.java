package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.domain.actor.GameNpc;
import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record NpcDescription(GameNpc npc) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("== " + Ansi.npc(npc.getName()) + " ==\n" + npc.getDescription() + "\n");
    }
}
