package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record NotEnoughMana(String spellName, int manaCost, int currentMana) implements OutputJsonMessage {

}
