package fr.idev.mudserver.network.message.connected;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.network.SecureOutputMessage;
import fr.idev.mudserver.network.server.telnet.OutputTelnetMessage;
import fr.idev.mudserver.network.server.telnet.TelnetOutput;

public record RequestPassword() implements OutputTelnetMessage, OutputJsonMessage, SecureOutputMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("Password : ");
    }

}
