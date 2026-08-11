package fr.idev.mudserver.game;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.Party;
import fr.idev.mudserver.domain.PartyMember;

/**
 * Matchmaking pré-jeu au Lobby, mémoire uniquement — une {@link Party} n'existe
 * que le temps de réunir un groupe avant un {@code world-enter}
 * ({@code controller.lobby.WorldEnter} appelle {@link #dissolve} juste après un
 * lancement réussi), jamais persistée (voir {@code multi-world.md} Phase D).
 * {@code partyIdByPendingInvite} s'auto-nettoie sans action explicite : une
 * fois une party dissoute/vidée, {@link #resolve} renvoie
 * {@code Optional#empty()} pour toute entrée qui pointait encore vers son id.
 */
@Service
public class PartyService {

    private final Map<UUID, Party> partiesById = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> partyIdByAccountId = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> partyIdByPendingInvite = new ConcurrentHashMap<>();

    public Optional<Party> partyOf(UUID accountId) {
        return resolve(partyIdByAccountId.get(accountId));
    }

    public Optional<Party> pendingInviteFor(UUID accountId) {
        return resolve(partyIdByPendingInvite.get(accountId));
    }

    private Optional<Party> resolve(UUID partyId) {
        return partyId == null ? Optional.empty() : Optional.ofNullable(partiesById.get(partyId));
    }

    public Party createParty(UUID leaderAccountId) {
        Party party = new Party(UUID.randomUUID(), leaderAccountId);
        partiesById.put(party.getId(), party);
        partyIdByAccountId.put(leaderAccountId, party.getId());
        return party;
    }

    public void invite(Party party, UUID targetAccountId) {
        party.invite(targetAccountId);
        partyIdByPendingInvite.put(targetAccountId, party.getId());
    }

    public void accept(Party party, UUID accountId) {
        party.accept(accountId);
        partyIdByAccountId.put(accountId, party.getId());
        partyIdByPendingInvite.remove(accountId);
    }

    public void leave(Party party, UUID accountId) {
        remove(party, accountId);
    }

    public void kick(Party party, UUID targetAccountId) {
        remove(party, targetAccountId);
    }

    private void remove(Party party, UUID accountId) {
        boolean empty = party.remove(accountId);
        partyIdByAccountId.remove(accountId);
        if (empty) {
            partiesById.remove(party.getId());
        }
    }

    /**
     * Appelée juste après un {@code world-enter} réussi : une party ayant lancé sa
     * partie ne doit pas rester joignable pour de nouveaux {@code party-invite}.
     */
    public void dissolve(Party party) {
        for (PartyMember member : party.getMembers()) {
            partyIdByAccountId.remove(member.accountId());
        }
        partiesById.remove(party.getId());
    }
}
