package app.network.message.ingame;

import java.util.List;
import java.util.UUID;

import app.network.OutputJsonMessage;
import app.domain.actor.AbstractNpc;

public record DialogueOptions(UUID npcId, String npcName, String greeting,
        List<AbstractNpc.NpcDialogueOption> options) implements OutputJsonMessage {

}
