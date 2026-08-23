package fr.idev.mudserver.network.message;

import java.util.List;

import fr.idev.mudserver.network.OutputJsonMessage;

public record Help(List<String> commands) implements OutputJsonMessage {

}
