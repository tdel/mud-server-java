package app.network.message.charselect;

import app.network.OutputJsonMessage;

public record InvalidGender(String input) implements OutputJsonMessage {

}
