package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.domain.actor.AbstractNpc;

public record NpcDescription(AbstractNpc npc) implements OutputJsonMessage {

}
