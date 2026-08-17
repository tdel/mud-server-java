package fr.idev.mudserver.domain.actor.system;

import java.util.Optional;

import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.actor.component.DialogueComponent;
import fr.idev.mudserver.domain.actor.component.DialogueComponent.DialogueOption;

@Service
public class DialogueSystem {

    public Optional<DialogueOption> resolveOption(DialogueComponent dialogue, String input) {
        try {
            int index = Integer.parseInt(input.trim());
            return index >= 1 && index <= dialogue.options().size()
                    ? Optional.of(dialogue.options().get(index - 1))
                    : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
