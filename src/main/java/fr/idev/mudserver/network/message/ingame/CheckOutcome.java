package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.game.CheckResult;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record CheckOutcome(CheckResult result) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        String proficiency = result.proficient() ? "proficient" : "not proficient";
        String outcome = result.success() ? "Success" : "Failure";
        output.write(String.format("%s check: %d vs DC %d (%s) — %s\n", result.label(), result.total(), result.dc(),
                proficiency, outcome));
    }
}
