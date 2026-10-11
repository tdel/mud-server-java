package app.network.message.ingame;

import java.util.List;

import app.network.OutputJsonMessage;

// Envoyé à la montée au level 20/40 : un changement de profession attend le
// joueur auprès du Grand Master (PNJ SKILL_LEARNER).
public record SubclassChoiceOffered(int tier, List<ClassOptionView> options) implements OutputJsonMessage {

}
