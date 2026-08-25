package app.network.message.ingame;

import app.network.OutputJsonMessage;
import app.domain.actor.AbstractNpc;

public record NpcDescription(AbstractNpc npc) implements OutputJsonMessage {

}
