package app.network.message.charselect;

import app.network.OutputJsonMessage;

public record NowPlaying(String characterName) implements OutputJsonMessage {

}
