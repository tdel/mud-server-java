package fr.idev.mudserver.game;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import fr.idev.mudserver.domain.HexCoordinate;
import fr.idev.mudserver.domain.RoomInstance;
import fr.idev.mudserver.domain.RoomTemplatePortal;
import fr.idev.mudserver.domain.TestRooms;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.GameMonster;
import fr.idev.mudserver.domain.actor.GameNpc;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.actor.TestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class HexGridRendererTest {

    @Test
    void rendersEachOccupantTypeAtItsExpectedPosition() {
        UUID targetId = UUID.randomUUID();
        RoomTemplatePortal portalTemplate = new RoomTemplatePortal(new HexCoordinate(3, 1), "NE", targetId,
                new HexCoordinate(0, 0));
        RoomInstance room = TestRooms.connectedByPortal(UUID.randomUUID(), "Testing Grounds", 5, 5,
                new HexCoordinate(2, 2), portalTemplate, targetId, "Elsewhere")[0];

        GamePlayer viewer = player("Viewer");
        room.join(viewer, new HexCoordinate(2, 2));

        GamePlayer other = player("Other");
        room.join(other, new HexCoordinate(2, 1));

        GameMonster monster = new GameMonster(UUID.randomUUID(), "Loup", UUID.randomUUID(), room.getId(),
                TestAttributes.of(10, 10, 10, 10, 10, 10), 10);
        room.placeMonster(monster, new HexCoordinate(3, 2));

        GameNpc npc = new GameNpc(UUID.randomUUID(), "Aubergiste", room.getId(), "...", null);
        room.placeNpc(npc, new HexCoordinate(1, 2));

        List<String> lines = HexGridRenderer.render(room, viewer, 1);

        assertThat(lines).containsExactly(" p #", "n @ m", " . .");
    }

    @Test
    void selfIsAlwaysAtTheCenterAndOutOfRoomCellsRenderAsTilde() {
        RoomInstance room = TestRooms.room(UUID.randomUUID(), "Corner RoomInstance", "...", null, 3, 3,
                new HexCoordinate(0, 0));
        GamePlayer viewer = player("Viewer");
        room.join(viewer, new HexCoordinate(0, 0));

        List<String> lines = HexGridRenderer.render(room, viewer, 1);
        String joined = String.join("", lines);

        assertThat(joined.chars().filter(c -> c == '@').count()).isEqualTo(1);
        assertThat(joined.chars().filter(c -> c == '~').count()).isEqualTo(4);
    }

    private GamePlayer player(String name) {
        return new GamePlayer(UUID.randomUUID(), UUID.randomUUID(), name, UUID.randomUUID(), Gender.MAN, Race.HUMAN,
                CharacterClass.FIGHTER, 1, 10, 10, TestAttributes.of(10, 10, 10, 10, 10, 10), 0, 0);
    }
}
