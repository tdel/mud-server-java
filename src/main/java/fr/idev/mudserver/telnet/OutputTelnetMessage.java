package fr.idev.mudserver.telnet;

import fr.idev.mudserver.network.OutputMessage;

public interface OutputTelnetMessage extends OutputMessage {
    void toTelnet(TelnetOutput output);
}
