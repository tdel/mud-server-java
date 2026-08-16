package fr.idev.mudserver.network.message.ingame;

import java.util.List;

import fr.idev.mudserver.domain.item.Rarity;
import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record ShopCatalog(String npcName, List<Entry> entries, int gold) implements OutputTelnetMessage {

    public record Entry(String itemName, Rarity rarity, int price) {
    }

    @Override
    public void toTelnet(TelnetOutput output) {
        StringBuilder text = new StringBuilder(
                "== " + Ansi.npc(npcName) + "'s wares == (you have " + Ansi.gold(gold) + " gold)");
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            text.append("\n  ").append(i + 1).append(". ").append(Ansi.item(entry.itemName(), entry.rarity()))
                    .append(" - ").append(Ansi.gold(entry.price())).append(" gold");
        }
        text.append("\n  0. Back\n");
        output.write(text.toString());
    }
}
