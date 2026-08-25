package app.network.message.connected;

import app.network.OutputJsonMessage;
import app.network.SecureOutputMessage;

public record RequestPassword() implements OutputJsonMessage, SecureOutputMessage {

}
