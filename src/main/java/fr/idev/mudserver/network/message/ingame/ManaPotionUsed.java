package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.domain.item.Rarity;
import fr.idev.mudserver.network.server.telnet.Ansi;
import fr.idev.mudserver.network.server.telnet.OutputTelnetMessage;
import fr.idev.mudserver.network.server.telnet.TelnetOutput;

public record ManaPotionUsed(String name, Rarity rarity, int restoredAmount, int currentMana,
        int maxMana) implements OutputTelnetMessage, OutputJsonMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("You drink the " + Ansi.item(name, rarity) + " and recover " + restoredAmount + " mana ("
                + currentMana + "/" + maxMana + ").\n");
    }
}
