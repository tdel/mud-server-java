package fr.idev.mudserver.domain.actor.component;

import java.util.List;

public record DialogueComponent(String greeting, List<DialogueOption> options) {

    public enum DialogueOptionType {
        RESPONSE, SHOP, LEAVE
    }

    public record DialogueOption(String label, DialogueOptionType type, String response) {
    }
}
