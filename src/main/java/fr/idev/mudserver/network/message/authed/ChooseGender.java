package fr.idev.mudserver.network.message.authed;

import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record ChooseGender() implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        StringBuilder text = new StringBuilder("Choose your character's gender:");
        for (Gender gender : Gender.values()) {
            text.append("\n  ").append(gender.label());
        }
        text.append("\n");
        output.write(text.toString());
    }
}
