package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record YouSaid(String text) implements OutputJsonMessage {

}
