package fr.idev.mudserver.network.server.telnet;

import fr.idev.mudserver.network.OutputMessage;

public interface OutputTelnetMessage extends OutputMessage {
    void toTelnet(TelnetOutput output);
}
