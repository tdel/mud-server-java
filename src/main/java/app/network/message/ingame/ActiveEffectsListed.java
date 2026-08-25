package app.network.message.ingame;

import java.util.List;

import app.network.OutputJsonMessage;

public record ActiveEffectsListed(List<EffectView> effects) implements OutputJsonMessage {

    public record EffectView(String spellName, String stat, int amount, long secondsRemaining) {
    }

}
