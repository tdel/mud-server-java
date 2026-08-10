package fr.idev.mudserver.network.message.ingame;

import java.util.List;

import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

/**
 * Boucle de sélection multiple pour {@code rest long}, même style numéroté que
 * {@link ShopCatalog} — sauf que chaque entrée est une instance d'inventoire
 * (pas un template) : sélectionner une entrée la retire de la liste affichée au
 * prochain tour (voir {@code controller.ingame.Rest}). {@code threshold} vient
 * de {@code RestService.LONG_REST_PROVISION_THRESHOLD}, passé par l'appelant
 * plutôt qu'importé ici pour ne pas coupler ce message à un bean Spring.
 */
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
