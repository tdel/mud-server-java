package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record YourTurn(int actionsRemaining, int extraActionsRemaining) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        String extra = extraActionsRemaining > 0 ? " and " + extraActionsRemaining + " extra action(s)" : "";
        output.write("It's your turn! You have " + actionsRemaining + " action(s)" + extra
                + ". attack <target> or use <item>.\n");
    }
}
