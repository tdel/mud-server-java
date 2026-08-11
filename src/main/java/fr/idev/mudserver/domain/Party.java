package fr.idev.mudserver.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class Party {

    private final UUID id;
    private UUID leaderAccountId;
    private final List<PartyMember> members = new ArrayList<>();
    private final Set<UUID> pendingInvites = new LinkedHashSet<>();

    public Party(UUID id, UUID leaderAccountId) {
        this.id = id;
        this.leaderAccountId = leaderAccountId;
        members.add(new PartyMember(leaderAccountId));
    }

    public UUID getId() {
        return id;
    }

    public UUID getLeaderAccountId() {
        return leaderAccountId;
    }

    public List<PartyMember> getMembers() {
        return List.copyOf(members);
    }

    public int size() {
        return members.size();
    }

    public boolean isLeader(UUID accountId) {
        return leaderAccountId.equals(accountId);
    }

    public boolean isMember(UUID accountId) {
        return members.stream().anyMatch(member -> member.accountId().equals(accountId));
    }

    public void invite(UUID accountId) {
        pendingInvites.add(accountId);
    }

    public void accept(UUID accountId) {
        pendingInvites.remove(accountId);
        members.add(new PartyMember(accountId));
    }

    public boolean remove(UUID accountId) {
        members.removeIf(member -> member.accountId().equals(accountId));
        pendingInvites.remove(accountId);
        if (members.isEmpty()) {
            return true;
        }
        if (leaderAccountId.equals(accountId)) {
            leaderAccountId = members.get(0).accountId();
        }
        return false;
    }
}
