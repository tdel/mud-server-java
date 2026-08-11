package fr.idev.mudserver.domain;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PartyTest {

    @Test
    void creatingAPartyMakesTheLeaderItsFirstMember() {
        UUID leaderId = UUID.randomUUID();
        Party party = new Party(UUID.randomUUID(), leaderId);

        assertThat(party.size()).isEqualTo(1);
        assertThat(party.isLeader(leaderId)).isTrue();
        assertThat(party.isMember(leaderId)).isTrue();
        assertThat(party.getMembers()).containsExactly(new PartyMember(leaderId));
    }

    @Test
    void acceptAddsAMemberAndClearsThePendingInvite() {
        UUID leaderId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();
        Party party = new Party(UUID.randomUUID(), leaderId);

        party.invite(inviteeId);
        party.accept(inviteeId);

        assertThat(party.size()).isEqualTo(2);
        assertThat(party.isMember(inviteeId)).isTrue();
    }

    @Test
    void removingANonLeaderMemberKeepsTheSameLeader() {
        UUID leaderId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Party party = new Party(UUID.randomUUID(), leaderId);
        party.invite(memberId);
        party.accept(memberId);

        boolean nowEmpty = party.remove(memberId);

        assertThat(nowEmpty).isFalse();
        assertThat(party.isLeader(leaderId)).isTrue();
        assertThat(party.isMember(memberId)).isFalse();
    }

    @Test
    void removingTheLeaderPromotesTheNextMemberByArrivalOrder() {
        UUID leaderId = UUID.randomUUID();
        UUID firstJoinerId = UUID.randomUUID();
        UUID secondJoinerId = UUID.randomUUID();
        Party party = new Party(UUID.randomUUID(), leaderId);
        party.invite(firstJoinerId);
        party.accept(firstJoinerId);
        party.invite(secondJoinerId);
        party.accept(secondJoinerId);

        boolean nowEmpty = party.remove(leaderId);

        assertThat(nowEmpty).isFalse();
        assertThat(party.isLeader(firstJoinerId)).isTrue();
        assertThat(party.getLeaderAccountId()).isEqualTo(firstJoinerId);
    }

    @Test
    void removingTheLastMemberReturnsTrue() {
        UUID leaderId = UUID.randomUUID();
        Party party = new Party(UUID.randomUUID(), leaderId);

        boolean nowEmpty = party.remove(leaderId);

        assertThat(nowEmpty).isTrue();
        assertThat(party.size()).isZero();
    }
}
