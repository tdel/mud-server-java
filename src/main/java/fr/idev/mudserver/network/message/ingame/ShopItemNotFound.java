package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record ShopItemNotFound(String input) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("\"" + input + "\" is not for sale here.\n");
    }
}
