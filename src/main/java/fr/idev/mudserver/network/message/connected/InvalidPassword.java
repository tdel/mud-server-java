package fr.idev.mudserver.network.message.connected;

import java.util.List;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.network.server.telnet.OutputTelnetMessage;
import fr.idev.mudserver.network.server.telnet.TelnetOutput;

public record InvalidPassword(List<String> reasons) implements OutputTelnetMessage, OutputJsonMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        for (String reason : reasons) {
            output.write(reason + "\n");
        }
    }
}
