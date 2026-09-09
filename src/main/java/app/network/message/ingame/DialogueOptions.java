package app.network.message.ingame;

import java.util.List;
import java.util.UUID;

import app.network.OutputJsonMessage;
import app.domain.actor.system.DialogueSystem;

public record DialogueOptions(UUID npcId, String npcName, String greeting,
        List<DialogueSystem.NpcDialogueOption> options) implements OutputJsonMessage {

}
