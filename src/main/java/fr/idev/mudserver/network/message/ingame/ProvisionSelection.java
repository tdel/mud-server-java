package fr.idev.mudserver.network.message.ingame;

import java.util.List;

import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record ProvisionSelection(List<Entry> entries, int selectedValue, int threshold) implements OutputTelnetMessage {

    public record Entry(String itemName, int nutritionValue) {
    }

    @Override
    public void toTelnet(TelnetOutput output) {
        StringBuilder text = new StringBuilder(
                "== Provisions (selected: " + Ansi.heal(selectedValue) + "/" + threshold + ") ==");
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            text.append("\n  ").append(i + 1).append(". ").append(entry.itemName()).append(" (")
                    .append(entry.nutritionValue()).append(")");
        }
        text.append("\n  done - confirm the long rest with what's selected\n  cancel - put everything back\n");
        output.write(text.toString());
    }
}
