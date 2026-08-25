package app.network.message.connected;

import java.util.List;

import app.network.OutputJsonMessage;

public record InvalidPassword(List<String> reasons) implements OutputJsonMessage {

}
