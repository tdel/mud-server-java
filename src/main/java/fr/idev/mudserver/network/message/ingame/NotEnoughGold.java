package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record NotEnoughGold(int price) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("You don't have enough gold for that (" + price + " needed).\n");
    }
}
