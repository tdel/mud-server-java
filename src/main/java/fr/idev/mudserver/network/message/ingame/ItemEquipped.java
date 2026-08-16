package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.domain.item.EquipmentSlot;
import fr.idev.mudserver.domain.item.Rarity;
import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record ItemEquipped(String name, Rarity rarity, EquipmentSlot slot) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("You equip the " + Ansi.item(name, rarity) + " (" + slot.label() + ").\n");
    }
}
