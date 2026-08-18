package fr.idev.mudserver.domain.actor.component;

import java.util.List;

public class DialogueComponent {

    public String greeting;
    public List<DialogueOption> options;

    public DialogueComponent(String greeting, List<DialogueOption> options) {
        this.greeting = greeting;
        this.options = options;
    }

    public enum DialogueOptionType {
        RESPONSE, SHOP, LEAVE
    }

    public record DialogueOption(String label, DialogueOptionType type, String response) {
    }
}
