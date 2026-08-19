package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.network.server.telnet.OutputTelnetMessage;
import fr.idev.mudserver.network.server.telnet.TelnetOutput;

public record YourTurn(int actionsRemaining,
        int extraActionsRemaining) implements OutputTelnetMessage, OutputJsonMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        String extra = extraActionsRemaining > 0 ? " and " + extraActionsRemaining + " extra action(s)" : "";
        output.write("It's your turn! You have " + actionsRemaining + " action(s)" + extra
                + ". attack <target> or use <item>.\n");
    }
}
