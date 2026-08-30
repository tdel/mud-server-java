package app.network.message.ingame;

import app.domain.actor.Subclass;
import app.network.OutputJsonMessage;

public record SubclassChosen(int tier, Subclass subclass) implements OutputJsonMessage {

}
