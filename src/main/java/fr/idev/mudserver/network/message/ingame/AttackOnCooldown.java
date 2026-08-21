package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.network.server.telnet.Ansi;
import fr.idev.mudserver.network.server.telnet.OutputTelnetMessage;
import fr.idev.mudserver.network.server.telnet.TelnetOutput;

public record AttackOnCooldown(long remainingMillis) implements OutputTelnetMessage, OutputJsonMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        double remainingSeconds = Math.ceil(remainingMillis / 1000.0);
        output.write(Ansi.error("You can't attack again yet (" + (int) remainingSeconds + "s left).") + "\n");
    }
}
