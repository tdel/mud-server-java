package app.network.message.ingame;

import java.util.UUID;

import app.domain.actor.AbstractNpc;
import app.network.OutputJsonMessage;
import app.network.server.tcpjson.TcpJsonOutput;

public record NpcDescription(AbstractNpc npc) implements OutputJsonMessage {

    public record Payload(UUID id, String name, String title, int level) {
    }

    @Override
    public void toJson(TcpJsonOutput output) {
        output.write("NpcDescription", new Payload(npc.getId(), npc.getName(), npc.getTitle(), npc.getLevel()));
    }
}
