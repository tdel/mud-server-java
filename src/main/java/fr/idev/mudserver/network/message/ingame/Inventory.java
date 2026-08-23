package fr.idev.mudserver.network.message.ingame;

import java.util.List;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.domain.item.Rarity;

public record Inventory(List<Entry> items, int gold) implements OutputJsonMessage {

    public record Entry(String name, Rarity rarity) {
    }

}
