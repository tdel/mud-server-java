package app.network.message.ingame;

import java.util.List;

import app.network.OutputJsonMessage;
import app.domain.actor.AbstractNpc;

public record DialogueOptions(String npcName, String greeting,
        List<AbstractNpc.NpcDialogueOption> options) implements OutputJsonMessage {

}
