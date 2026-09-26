package app.network.message.ingame;

import app.network.OutputJsonMessage;

// Refus d'équiper `name` en main secondaire : une arme à deux mains est tenue
// (cf. InventorySystem.isOffHandBlocked).
public record OffHandBlocked(String name) implements OutputJsonMessage {

}
