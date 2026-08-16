package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.domain.item.Rarity;
import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record ItemUsed(String name, Rarity rarity, int healedAmount, int currentHealth,
        int maxHealth) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("You drink the " + Ansi.item(name, rarity) + " and recover " + Ansi.heal(healedAmount) + " HP ("
                + currentHealth + "/" + maxHealth + ").\n");
    }
}
