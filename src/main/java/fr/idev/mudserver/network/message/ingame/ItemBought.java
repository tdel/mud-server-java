package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.domain.item.Rarity;
import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record ItemBought(String itemName, Rarity rarity, int price) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("You buy a " + Ansi.item(itemName, rarity) + " for " + Ansi.gold(price) + " gold.\n");
    }
}
