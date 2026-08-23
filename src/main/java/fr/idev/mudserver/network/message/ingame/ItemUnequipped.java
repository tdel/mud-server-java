package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.domain.item.Rarity;

public record ItemUnequipped(String name, Rarity rarity) implements OutputJsonMessage {

}
