package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record ShopItemNotFound(String input) implements OutputJsonMessage {

}
