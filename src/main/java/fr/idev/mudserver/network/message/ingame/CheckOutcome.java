package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.game.dice.CheckResult;
import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record CheckOutcome(CheckResult result) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        String proficiency = result.proficient() ? "proficient" : "not proficient";
        String disadvantage = result.disadvantage() ? ", disadvantage" : "";
        String outcome = result.success() ? Ansi.success("Success") : Ansi.error("Failure");
        output.write(String.format("%s check: %d vs DC %d (%s%s) — %s\n", result.label(), result.total(), result.dc(),
                proficiency, disadvantage, outcome));
    }
}
