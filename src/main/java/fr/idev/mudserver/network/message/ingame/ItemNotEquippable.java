package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.domain.item.Item;
import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record ItemNotEquippable(Item item) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write(Ansi.error("You can't equip the " + item.getTemplate().getName() + ".") + "\n");
    }
}
