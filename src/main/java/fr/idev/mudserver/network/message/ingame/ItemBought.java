package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record ItemBought(String itemName, int price) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("You buy a " + itemName + " for " + price + " gold.\n");
    }
}
