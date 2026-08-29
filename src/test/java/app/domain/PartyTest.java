package app.domain;

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

import app.domain.actor.Attribute;
import app.domain.actor.CharacterClass;
import app.domain.actor.Gender;
import app.domain.actor.Race;
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
import app.network.message.ingame.PartyMemberLeft;

class PartyTest {

    @Test
    void constructorAttachesLeaderAsSoleMember() {
        CharacterInstance leader = newCharacter();

        Party party = new Party(leader);

        assertThat(party.getLeader()).isEqualTo(leader);
        assertThat(party.getMembers()).containsExactly(leader);
        assertThat(leader.getParty()).isEqualTo(party);
    }

    @Test
    void addMemberJoinsPartyAndAttachesBackReference() {
        CharacterInstance leader = newCharacter();
        CharacterInstance joiner = newCharacter();
        Party party = new Party(leader);

        party.addMember(joiner);

        assertThat(party.getMembers()).containsExactly(leader, joiner);
        assertThat(joiner.getParty()).isEqualTo(party);
    }

    @Test
    void removePromotesNextMemberByArrivalOrderWhenLeaderLeaves() {
        CharacterInstance leader = newCharacter();
        CharacterInstance second = newCharacter();
        CharacterInstance third = newCharacter();
        Party party = new Party(leader);
        party.addMember(second);
        party.addMember(third);

        party.remove(leader);

        assertThat(party.getLeader()).isEqualTo(second);
        assertThat(party.getMembers()).containsExactly(second, third);
        assertThat(leader.getParty()).isNull();
    }

    @Test
    void removeEmptiesPartyWhenLastMemberLeaves() {
        CharacterInstance leader = newCharacter();
        Party party = new Party(leader);

        party.remove(leader);

        assertThat(party.isEmpty()).isTrue();
        assertThat(party.getLeader()).isNull();
    }

    @Test
    void removeAndNotifyBroadcastsMemberLeftAndNewLeaderWhenLeaderLeaves() {
        FakeConnection leaderConnection = new FakeConnection();
        FakeConnection secondConnection = new FakeConnection();
        CharacterInstance leader = newCharacter();
        leader.setConnection(leaderConnection);
        CharacterInstance second = newCharacter();
        second.setConnection(secondConnection);
        Party party = new Party(leader);
        party.addMember(second);

        party.removeAndNotify(leader);

        assertThat(secondConnection.sent).hasSize(2);
        assertThat(secondConnection.sent.get(0)).isInstanceOf(PartyMemberLeft.class);
        assertThat(secondConnection.sent.get(1)).isInstanceOf(NewPartyLeader.class);
        assertThat(party.getLeader()).isEqualTo(second);
    }

    @Test
    void removeAndNotifyDoesNotBroadcastWhenPartyBecomesEmpty() {
        FakeConnection leaderConnection = new FakeConnection();
        CharacterInstance leader = newCharacter();
        leader.setConnection(leaderConnection);
        Party party = new Party(leader);

        party.removeAndNotify(leader);

        assertThat(leaderConnection.sent).isEmpty();
        assertThat(party.isEmpty()).isTrue();
    }

    @Test
    void disbandDetachesEveryoneAndEmptiesTheParty() {
        CharacterInstance leader = newCharacter();
        CharacterInstance second = newCharacter();
        Party party = new Party(leader);
        party.addMember(second);

        party.disband();

        assertThat(party.isEmpty()).isTrue();
        assertThat(party.getLeader()).isNull();
        assertThat(leader.getParty()).isNull();
        assertThat(second.getParty()).isNull();
    }

    @Test
    void broadcastExcludesGivenMember() {
        FakeConnection leaderConnection = new FakeConnection();
        FakeConnection secondConnection = new FakeConnection();
        CharacterInstance leader = newCharacter();
        leader.setConnection(leaderConnection);
        CharacterInstance second = newCharacter();
        second.setConnection(secondConnection);
        Party party = new Party(leader);
        party.addMember(second);

        party.broadcast(new PartyMemberLeft("someone"), leader);

        assertThat(leaderConnection.sent).isEmpty();
        assertThat(secondConnection.sent).hasSize(1);
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
