package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record EquipmentLooted(String itemName) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("You loot a " + Ansi.item(itemName) + ".\n");
    }
}
