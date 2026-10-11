package app.network.message.ingame;

import app.network.OutputJsonMessage;

// requiredLevel : level du prochain changement de profession, null si les deux
// ont déjà été faits.
public record NoPendingSubclassChoice(Integer requiredLevel) implements OutputJsonMessage {

}
