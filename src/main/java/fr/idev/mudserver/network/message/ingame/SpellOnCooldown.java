package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.network.server.telnet.Ansi;
import fr.idev.mudserver.network.server.telnet.OutputTelnetMessage;
import fr.idev.mudserver.network.server.telnet.TelnetOutput;

public record SpellOnCooldown(String spellName,
        long remainingMillis) implements OutputTelnetMessage, OutputJsonMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        double remainingSeconds = Math.ceil(remainingMillis / 1000.0);
        output.write(Ansi.error(spellName + " isn't ready yet (" + (int) remainingSeconds + "s left).") + "\n");
    }
}
