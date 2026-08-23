package fr.idev.mudserver.network.message.charselect;

import fr.idev.mudserver.network.OutputJsonMessage;

public record CharacterNameTaken(String name) implements OutputJsonMessage {

}
