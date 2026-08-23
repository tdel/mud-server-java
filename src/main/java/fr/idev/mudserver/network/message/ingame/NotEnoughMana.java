package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;

public record NotEnoughMana(String spellName, int manaCost, int currentMana) implements OutputJsonMessage {

}
