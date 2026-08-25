package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record NotEnoughGold(int price) implements OutputJsonMessage {

}
