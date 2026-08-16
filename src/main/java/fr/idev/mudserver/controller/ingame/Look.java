package fr.idev.mudserver.controller.ingame;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.world.RoomInstance;
import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.instance.MonsterInstance;
import fr.idev.mudserver.domain.actor.AbstractNpc;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.game.HexGridRenderer;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.ingame.RoomDescription;

@Component
public class Look implements ControllerHandler {

    private final HexGridRenderer hexGridRenderer;

    public Look(HexGridRenderer hexGridRenderer) {
        this.hexGridRenderer = hexGridRenderer;
    }

    @Override
    public String name() {
        return "look";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();
        connection.send(describeRoom(character));
    }

    private RoomDescription describeRoom(CharacterInstance character) {
        RoomInstance room = character.getCurrentRoom();

        List<String> gridLines = hexGridRenderer.render(room, character);
        List<AbstractCharacter> nearby = room.occupantsWithin(character.getPosition(), HexGridRenderer.VIEWPORT_RADIUS);

        List<String> portalSummaries = room.getPortals().stream()
                .map(portal -> portal.direction() + ": " + portal.targetRoom().getName()).toList();
        List<String> characterNames = nearby.stream().filter(CharacterInstance.class::isInstance)
                .filter(other -> !other.getId().equals(character.getId())).map(AbstractCharacter::getName).toList();
        List<String> monsterNames = nearby.stream().filter(MonsterInstance.class::isInstance)
                .map(AbstractCharacter::getName).toList();
        List<String> npcNames = nearby.stream().filter(AbstractNpc.class::isInstance).map(AbstractCharacter::getName)
                .toList();

        return new RoomDescription(room.getName(), room.getDescription(), gridLines, HexGridRenderer.LEGEND,
                portalSummaries, characterNames, monsterNames, npcNames);
    }
}
