package app.network.message.ingame;

import java.util.List;

import app.domain.actor.Subclass;
import app.network.OutputJsonMessage;

public record InvalidSubclass(String input, List<Subclass> options) implements OutputJsonMessage {

}
