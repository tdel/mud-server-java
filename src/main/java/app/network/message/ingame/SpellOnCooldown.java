package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record SpellOnCooldown(String spellName, long remainingMillis) implements OutputJsonMessage {

}
