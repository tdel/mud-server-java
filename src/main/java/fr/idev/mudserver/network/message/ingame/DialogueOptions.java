package fr.idev.mudserver.network.message.ingame;

import java.util.List;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.domain.actor.AbstractNpc;

public record DialogueOptions(String npcName, String greeting,
        List<AbstractNpc.NpcDialogueOption> options) implements OutputJsonMessage {

}
