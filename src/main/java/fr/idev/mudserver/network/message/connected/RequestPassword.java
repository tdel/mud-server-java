package fr.idev.mudserver.network.message.connected;

import fr.idev.mudserver.network.SecureOutputMessage;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record RequestPassword() implements OutputTelnetMessage, SecureOutputMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("Password : ");
    }

}
