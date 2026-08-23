package fr.idev.mudserver.network.message.connected;

import java.util.List;

import fr.idev.mudserver.network.OutputJsonMessage;

public record InvalidPassword(List<String> reasons) implements OutputJsonMessage {

}
