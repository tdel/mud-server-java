package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record InvalidShotGrade(String argument) implements OutputJsonMessage {

}
