package fr.idev.mudserver.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Matchmaking pré-jeu au Lobby : jamais persistée (contrairement à
 * {@link WorldInstance}), vit uniquement en mémoire dans {@code PartyService}
 * le temps de réunir un groupe avant un {@code world-enter}. Le leader change
 * si celui-ci quitte via {@link #remove} : promotion du membre suivant par
 * ordre d'arrivée plutôt que dissolution, sauf si c'était le dernier membre
 * (voir {@code multi-world.md} Phase D).
 */
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

    /**
     * Retire un membre (départ volontaire ou {@code party-kick}) ; promeut le
     * suivant par ordre d'arrivée si c'était le leader. Renvoie {@code true} si la
     * party est maintenant vide, à dissoudre par l'appelant (voir
     * {@code PartyService}).
     */
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
