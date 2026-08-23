package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.domain.item.EquipmentSlot;
import fr.idev.mudserver.domain.item.Rarity;

public record ItemEquipped(String name, Rarity rarity, EquipmentSlot slot) implements OutputJsonMessage {

}
