package app.domain.actor.system;

import java.util.List;
import java.util.Optional;

public final class DialogueSystem {

    private final NpcDialogue dialogue;

    public DialogueSystem(NpcDialogue dialogue) {
        this.dialogue = dialogue;
    }

    public Optional<NpcDialogue> getDialogue() {
        return Optional.ofNullable(dialogue);
    }

    public enum NpcDialogueOptionType {
        // SKILL_LEARN : ouvre la liste des compétences à apprendre (commande
        // skill-list, PNJ de type SKILL_LEARNER uniquement).
        RESPONSE, SHOP, SKILL_LEARN, LEAVE
    }

    public record NpcDialogue(String greeting, List<NpcDialogueOption> options) {
    }

    public record NpcDialogueOption(String label, NpcDialogueOptionType type, String response) {
    }
}
