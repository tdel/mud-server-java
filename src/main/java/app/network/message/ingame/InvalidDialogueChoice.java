package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record InvalidDialogueChoice(String input) implements OutputJsonMessage {

}
