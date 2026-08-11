package fr.idev.mudserver.domain;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.GameNpc;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.actor.TestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class RoomTest {

    private static final int WIDTH = 5;
    private static final int HEIGHT = 4;

    private final RoomInstance room = TestRooms.room(UUID.randomUUID(), "Testing Grounds", "...", null, WIDTH, HEIGHT,
            new HexCoordinate(2, 2));

    @Test
    void isInBoundsAcceptsOnlyCellsWithinTheGridDimensions() {
        assertThat(room.isInBounds(new HexCoordinate(0, 0))).isTrue();
        assertThat(room.isInBounds(new HexCoordinate(WIDTH - 1, HEIGHT - 1))).isTrue();
        assertThat(room.isInBounds(new HexCoordinate(WIDTH, 0))).isFalse();
        assertThat(room.isInBounds(new HexCoordinate(0, HEIGHT))).isFalse();
        assertThat(room.isInBounds(new HexCoordinate(-1, 0))).isFalse();
    }

    @Test
    void isBorderCellIsTrueOnlyOnTheEdgesOfTheGrid() {
        assertThat(room.isBorderCell(new HexCoordinate(0, 0))).isTrue();
        assertThat(room.isBorderCell(new HexCoordinate(WIDTH - 1, 0))).isTrue();
        assertThat(room.isBorderCell(new HexCoordinate(0, HEIGHT - 1))).isTrue();
        assertThat(room.isBorderCell(new HexCoordinate(2, 2))).isFalse();
        assertThat(room.isBorderCell(new HexCoordinate(WIDTH, 0))).isFalse();
    }

    @Test
    void findPortalAtReturnsThePortalOnlyOnItsExactCell() {
        UUID targetId = UUID.randomUUID();
        RoomTemplatePortal portalTemplate = new RoomTemplatePortal(new HexCoordinate(0, 0), "W", targetId,
                new HexCoordinate(4, 2));
        RoomInstance[] rooms = TestRooms.connectedByPortal(UUID.randomUUID(), "Testing Grounds", WIDTH, HEIGHT,
                new HexCoordinate(2, 2), portalTemplate, targetId, "Target");
        RoomInstance source = rooms[0];
        RoomInstance target = rooms[1];

        assertThat(source.findPortalAt(new HexCoordinate(0, 0)))
                .hasValueSatisfying(portal -> assertThat(portal.targetRoom()).isEqualTo(target));
        assertThat(source.findPortalAt(new HexCoordinate(1, 0))).isEmpty();
    }

    @Test
    void tryClaimCellSucceedsOnceThenFailsUntilReleased() {
        HexCoordinate cell = new HexCoordinate(1, 1);
        GamePlayer alice = player("Alice");
        GamePlayer bob = player("Bob");

        assertThat(room.tryClaimCell(cell, alice)).isTrue();
        assertThat(room.tryClaimCell(cell, bob)).isFalse();
        assertThat(room.occupantAt(cell)).contains(alice);

        room.releaseCell(cell, alice);

        assertThat(room.tryClaimCell(cell, bob)).isTrue();
        assertThat(room.occupantAt(cell)).contains(bob);
    }

    @Test
    void joinFallsBackToTheNearestFreeCellWhenTheDesiredCellIsAlreadyOccupied() {
        GamePlayer alice = player("Alice");
        GamePlayer bob = player("Bob");

        room.join(alice, new HexCoordinate(2, 2));
        room.join(bob, new HexCoordinate(2, 2));

        assertThat(alice.getPosition()).isEqualTo(new HexCoordinate(2, 2));
        assertThat(bob.getPosition()).isNotEqualTo(new HexCoordinate(2, 2));
        assertThat(room.occupantAt(bob.getPosition())).contains(bob);
    }

    @Test
    void leaveReleasesTheOccupiedCell() {
        GamePlayer alice = player("Alice");
        room.join(alice, new HexCoordinate(1, 1));

        room.leave(alice);

        assertThat(room.occupantAt(new HexCoordinate(1, 1))).isEmpty();
        assertThat(alice.getPosition()).isNull();
    }

    @Test
    void findNpcByNameIsCaseInsensitiveAndIgnoresOtherOccupants() {
        GameNpc innkeeper = new GameNpc(UUID.randomUUID(), "Aubergiste", room.getId(), "...", null);
        room.placeNpc(innkeeper, new HexCoordinate(1, 1));

        assertThat(room.findNpcByName("aubergiste")).contains(innkeeper);
        assertThat(room.findNpcByName("inconnu")).isEmpty();
    }

    private GamePlayer player(String name) {
        Account account = new Account(UUID.randomUUID(), "room-" + UUID.randomUUID(), "hashed-password", null);
        return new GamePlayer(UUID.randomUUID(), account, name, room, Gender.MAN, Race.HUMAN, CharacterClass.FIGHTER, 1,
                10, 10, TestAttributes.of(10, 10, 10, 10, 10, 10), 0, 0);
    }
}
