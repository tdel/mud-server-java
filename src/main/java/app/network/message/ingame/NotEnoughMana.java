package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record NotEnoughMana(String skillName, int manaCost, int currentMana) implements OutputJsonMessage {

}
