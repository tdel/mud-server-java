package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.domain.actor.AbstractNpc;
import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record NpcDescription(AbstractNpc npc) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("== " + Ansi.npc(npc.getName()) + " ==\n" + npc.getDescription() + "\n");
    }
}
