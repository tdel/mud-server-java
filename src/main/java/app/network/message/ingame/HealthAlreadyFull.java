package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record HealthAlreadyFull(String name, int currentHealth, int maxHealth) implements OutputJsonMessage {

}
