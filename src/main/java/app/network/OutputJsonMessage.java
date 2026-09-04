package app.network;

import app.network.server.tcpjson.TcpJsonOutput;

public interface OutputJsonMessage extends OutputMessage {

    default void toJson(TcpJsonOutput output) {
        output.write(getClass().getSimpleName(), this);
    }
}
