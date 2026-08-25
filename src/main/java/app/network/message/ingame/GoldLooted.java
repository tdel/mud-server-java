package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record GoldLooted(int amount) implements OutputJsonMessage {

}
