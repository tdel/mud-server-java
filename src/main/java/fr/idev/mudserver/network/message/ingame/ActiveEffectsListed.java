package fr.idev.mudserver.network.message.ingame;

import java.util.List;

import fr.idev.mudserver.network.OutputJsonMessage;

public record ActiveEffectsListed(List<EffectView> effects) implements OutputJsonMessage {

    public record EffectView(String spellName, String stat, int amount, long secondsRemaining) {
    }

}
