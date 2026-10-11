package app.network.message.ingame;

import java.util.List;

import app.network.OutputJsonMessage;

public record InvalidSubclass(String input, List<ClassOptionView> options) implements OutputJsonMessage {

}
