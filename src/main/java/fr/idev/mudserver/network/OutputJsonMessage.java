package fr.idev.mudserver.network;

import fr.idev.mudserver.network.server.tcpjson.TcpJsonOutput;

public interface OutputJsonMessage extends OutputMessage {

    default void toJson(TcpJsonOutput output) {
        output.write(getClass().getSimpleName(), this, this instanceof SecureOutputMessage);
    }
}
