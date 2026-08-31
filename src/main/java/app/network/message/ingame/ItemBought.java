package app.network.message.ingame;

import app.network.OutputJsonMessage;
import app.domain.item.ItemGrade;

public record ItemBought(String itemName, ItemGrade grade, int price) implements OutputJsonMessage {

}
