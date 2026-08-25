package app.network.message;

import java.util.List;

import app.network.OutputJsonMessage;

public record Help(List<String> commands) implements OutputJsonMessage {

}
