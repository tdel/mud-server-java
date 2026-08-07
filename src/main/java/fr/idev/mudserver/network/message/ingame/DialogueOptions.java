package fr.idev.mudserver.network.message.ingame;

import java.util.List;

import fr.idev.mudserver.domain.actor.GameNpc;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record DialogueOptions(String npcName, String greeting,
        List<GameNpc.NpcDialogueOption> options) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        StringBuilder text = new StringBuilder("== " + npcName + " ==\n" + greeting);
        for (int i = 0; i < options.size(); i++) {
            text.append("\n  ").append(i + 1).append(". ").append(options.get(i).label());
        }
        text.append("\n");
        output.write(text.toString());
    }
}
