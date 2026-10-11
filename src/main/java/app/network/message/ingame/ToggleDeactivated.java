package app.network.message.ingame;

import app.network.OutputJsonMessage;

// Un toggle (Vicious Stance...) a été désactivé, par le joueur ou faute de PM.
public record ToggleDeactivated(String skillName, boolean outOfMana) implements OutputJsonMessage {
}
