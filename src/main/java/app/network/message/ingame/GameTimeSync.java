package app.network.message.ingame;

import app.domain.world.DayPhase;
import app.network.OutputJsonMessage;

public record GameTimeSync(int hour, int minute, DayPhase phase) implements OutputJsonMessage {

}
