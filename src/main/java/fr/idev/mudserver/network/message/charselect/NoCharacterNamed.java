package fr.idev.mudserver.network.message.charselect;

import fr.idev.mudserver.network.OutputJsonMessage;

public record NoCharacterNamed(String name) implements OutputJsonMessage {

}
