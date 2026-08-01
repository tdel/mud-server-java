package fr.idev.mudserver.network.message.connected;

import java.util.List;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record InvalidPassword(List<String> reasons) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        for (String reason : reasons) {
            output.write(reason + "\n");
        }
    }
}
