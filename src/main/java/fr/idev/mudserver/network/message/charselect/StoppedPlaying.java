package fr.idev.mudserver.network.message.charselect;

import fr.idev.mudserver.network.OutputJsonMessage;

public record StoppedPlaying(String characterName) implements OutputJsonMessage {

}
