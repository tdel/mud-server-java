package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;

public record Chat(String speakerName, String text) implements OutputJsonMessage {

}
