package app.domain;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import app.domain.actor.instance.CharacterInstance;
import app.network.OutputMessage;
import app.network.message.ingame.NewPartyLeader;
import app.network.message.ingame.PartyMemberLeft;

public class Party {

    private final UUID id;
    private volatile CharacterInstance leader;
    private final List<CharacterInstance> members = new CopyOnWriteArrayList<>();

    public Party(CharacterInstance leader) {
        this.id = UUID.randomUUID();
        this.leader = leader;
        members.add(leader);
        leader.setParty(this);
    }

    public UUID getId() {
        return id;
    }

    public CharacterInstance getLeader() {
        return leader;
    }

    public List<CharacterInstance> getMembers() {
        return List.copyOf(members);
    }

    public int size() {
        return members.size();
    }

    public boolean isEmpty() {
        return members.isEmpty();
    }

    public boolean isLeader(CharacterInstance character) {
        return leader == character;
    }

    public boolean isMember(CharacterInstance character) {
        return members.contains(character);
    }

    public void addMember(CharacterInstance character) {
        members.add(character);
        character.setParty(this);
    }

    public void remove(CharacterInstance character) {
        members.remove(character);
        character.setParty(null);
        if (members.isEmpty()) {
            leader = null;
            return;
        }
        if (leader == character) {
            leader = members.get(0);
        }
    }

    public void removeAndNotify(CharacterInstance character) {
        boolean wasLeader = isLeader(character);
        remove(character);
        if (!isEmpty()) {
            broadcast(new PartyMemberLeft(character.getName()), null);
            if (wasLeader) {
                broadcast(new NewPartyLeader(leader.getId(), leader.getName()), null);
            }
        }
    }

    public void disband() {
        for (CharacterInstance member : members) {
            member.setParty(null);
        }
        members.clear();
        leader = null;
    }

    public void broadcast(OutputMessage message, CharacterInstance exclude) {
        for (CharacterInstance member : members) {
            if (member != exclude) {
                member.send(message);
            }
        }
    }
}
