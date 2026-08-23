package fr.idev.mudserver.network.message.ingame;

import java.util.List;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.network.server.telnet.OutputTelnetMessage;
import fr.idev.mudserver.network.server.telnet.TelnetOutput;

public record ActiveEffectsListed(List<EffectView> effects) implements OutputTelnetMessage, OutputJsonMessage {

    public record EffectView(String spellName, String stat, int amount, long secondsRemaining) {
    }

    @Override
    public void toTelnet(TelnetOutput output) {
        if (effects.isEmpty()) {
            output.write("No active effects.\n");
            return;
        }

        output.write("Active effects:\n");
        for (EffectView effect : effects) {
            String signedAmount = effect.amount() >= 0 ? "+" + effect.amount() : String.valueOf(effect.amount());
            output.write("  " + effect.spellName() + ": " + signedAmount + " to " + effect.stat() + " ("
                    + effect.secondsRemaining() + "s remaining)\n");
        }
    }
}
