package fr.idev.mudserver.network.message.connected;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.network.SecureOutputMessage;

public record RequestPassword() implements OutputJsonMessage, SecureOutputMessage {

}
