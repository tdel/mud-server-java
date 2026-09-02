package app.network.message.ingame;

import java.util.List;

import app.domain.StatModifier;
import app.network.OutputJsonMessage;

public record ActiveEffectsListed(List<EffectView> effects) implements OutputJsonMessage {

    public record EffectView(String skillName, List<StatModifier> modifiers, long secondsRemaining) {
    }

}
