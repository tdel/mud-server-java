package app.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import app.domain.Account;
import app.domain.Party;
import app.domain.PendingPartyInvite;
import app.domain.actor.Attribute;
import app.domain.actor.CharacterClass;
import app.domain.actor.Gender;
import app.domain.actor.Race;
import app.domain.actor.event.PlayerRemovedFromWorld;
import app.domain.actor.instance.CharacterInstance;
import app.domain.map.Position;
import app.domain.world.CollisionGrid;
import app.domain.world.WorldInstance;
import app.domain.world.ZoneInstance;
import app.domain.world.ZoneTemplate;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.OutputMessage;
import app.network.message.ingame.NewPartyLeader;
import app.network.message.ingame.PartyInviteDeclined;
import app.network.message.ingame.PartyMemberLeft;

class PartyServiceTest {

    private final PartyService service = new PartyService(null);

    @Test
    void expireInvitesDeclinesInviteOlderThanTimeoutOnBothSides() {
        FakeConnection inviterConnection = new FakeConnection();
        FakeConnection targetConnection = new FakeConnection();
        CharacterInstance inviter = newCharacter();
        inviter.setConnection(inviterConnection);
        CharacterInstance target = newCharacter();
        target.setConnection(targetConnection);
        Party party = new Party(inviter);
        long sentAtMillis = System.currentTimeMillis() - PartyService.INVITE_TIMEOUT_MS - 1_000L;
        target.setPendingInvite(new PendingPartyInvite(party, inviter, sentAtMillis));

        service.expireInvites(List.of(inviter, target));

        assertThat(target.getPendingInvite()).isNull();
        assertThat(targetConnection.sent).hasSize(1).first().isInstanceOf(PartyInviteDeclined.class);
        assertThat(inviterConnection.sent).hasSize(1).first().isInstanceOf(PartyInviteDeclined.class);
    }

    @Test
    void expireInvitesLeavesFreshInviteUntouched() {
        CharacterInstance inviter = newCharacter();
        CharacterInstance target = newCharacter();
        Party party = new Party(inviter);
        PendingPartyInvite invite = new PendingPartyInvite(party, inviter, System.currentTimeMillis());
        target.setPendingInvite(invite);

        service.expireInvites(List.of(inviter, target));

        assertThat(target.getPendingInvite()).isEqualTo(invite);
    }

    @Test
    void onPlayerRemovedFromWorldClearsPendingInvite() {
        CharacterInstance target = newCharacter();
        target.setPendingInvite(
                new PendingPartyInvite(new Party(newCharacter()), newCharacter(), System.currentTimeMillis()));

        service.onPlayerRemovedFromWorld(new PlayerRemovedFromWorld(target));

        assertThat(target.getPendingInvite()).isNull();
    }

    @Test
    void onPlayerRemovedFromWorldMakesDisconnectingLeaderLeavePartyAndPromotesNext() {
        FakeConnection secondConnection = new FakeConnection();
        CharacterInstance leader = newCharacter();
        CharacterInstance second = newCharacter();
        second.setConnection(secondConnection);
        Party party = new Party(leader);
        party.addMember(second);

        service.onPlayerRemovedFromWorld(new PlayerRemovedFromWorld(leader));

        assertThat(leader.getParty()).isNull();
        assertThat(party.getLeader()).isEqualTo(second);
        assertThat(secondConnection.sent).hasSize(2);
        assertThat(secondConnection.sent.get(0)).isInstanceOf(PartyMemberLeft.class);
        assertThat(secondConnection.sent.get(1)).isInstanceOf(NewPartyLeader.class);
    }

    @Test
    void onPlayerRemovedFromWorldIsNoopWhenNotInAParty() {
        CharacterInstance character = newCharacter();

        service.onPlayerRemovedFromWorld(new PlayerRemovedFromWorld(character));

        assertThat(character.getParty()).isNull();
    }

    private static CharacterInstance newCharacter() {
        Account account = new Account(UUID.randomUUID(), "login", "password");
        Map<Attribute, Integer> attributes = new EnumMap<>(Attribute.class);
        for (Attribute attribute : Attribute.values()) {
            attributes.put(attribute, 10);
        }
        ZoneInstance zone = newZone();
        CharacterInstance character = new CharacterInstance(UUID.randomUUID(), account, "Test Character", zone,
                Gender.MAN, Race.HUMAN, CharacterClass.FIGHTER, 1, 50, 50, attributes, 0, 0, 0, 10, 10, Set.of(),
                List.of());
        character.setWorldInstance(zone.getWorldInstance());
        zone.join(character);
        return character;
    }

    private static ZoneInstance newZone() {
        CollisionGrid terrain = new CollisionGrid(1, 1, 1.0, new BitSet());
        ZoneTemplate template = new ZoneTemplate(UUID.randomUUID(), "Town", "description", true, terrain,
                new Position(0, 0), List.of(), List.of(), List.of());
        WorldInstance worldInstance = new WorldInstance(UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        ZoneInstance zone = new ZoneInstance(UUID.randomUUID(), template, worldInstance);
        worldInstance.setZoneInstances(Map.of(template.getId(), zone));
        return zone;
    }

    private static class FakeConnection implements Connection {

        final List<OutputMessage> sent = new ArrayList<>();

        @Override
        public void requestBlocking(OutputMessage message, Consumer<String> handler) {
        }

        @Override
        public ConnectionState state() {
            return ConnectionState.INGAME;
        }

        @Override
        public void setState(ConnectionState state) {
        }

        @Override
        public void send(OutputMessage message) {
            sent.add(message);
        }

        @Override
        public void close() {
        }

        @Override
        public void attachCharacter(CharacterInstance character) {
        }

        @Override
        public CharacterInstance character() {
            return null;
        }

        @Override
        public void setAccount(Account account) {
        }

        @Override
        public Account account() {
            return null;
        }

        @Override
        public void attachWorldInstance(WorldInstance worldInstance) {
        }

        @Override
        public void detachWorldInstance() {
        }

        @Override
        public WorldInstance worldInstance() {
            return null;
        }
    }
}
