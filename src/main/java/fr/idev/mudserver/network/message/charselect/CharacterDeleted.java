package fr.idev.mudserver.network.message.charselect;

import fr.idev.mudserver.network.OutputJsonMessage;

public record CharacterDeleted(String name) implements OutputJsonMessage {

}
