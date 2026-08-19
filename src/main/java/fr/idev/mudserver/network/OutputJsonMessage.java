package fr.idev.mudserver.network;

import fr.idev.mudserver.network.server.tui.JsonOutput;

public interface OutputJsonMessage extends OutputMessage {

    default void toJson(JsonOutput output) {
        output.write(getClass().getSimpleName(), this, this instanceof SecureOutputMessage);
    }
}
