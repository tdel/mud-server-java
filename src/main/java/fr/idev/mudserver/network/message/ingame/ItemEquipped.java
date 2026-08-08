package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.domain.EquipmentSlot;
import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record ItemEquipped(String name, EquipmentSlot slot) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("You equip the " + Ansi.item(name) + " (" + slot.label() + ").\n");
    }
}
