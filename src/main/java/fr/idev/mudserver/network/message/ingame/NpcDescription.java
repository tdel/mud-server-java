package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.domain.actor.component.IdentityComponent;

import fr.idev.mudserver.domain.actor.AbstractNpc;
import fr.idev.mudserver.domain.actor.component.NpcDescriptorComponent;
import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record NpcDescription(AbstractNpc npc) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        String description = npc.component(NpcDescriptorComponent.class).description;
        output.write("== " + Ansi.npc(npc.component(IdentityComponent.class).name) + " ==\n" + description + "\n");
    }
}
