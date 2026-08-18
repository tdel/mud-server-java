package fr.idev.mudserver.domain.combat;

import fr.idev.mudserver.domain.actor.component.IdentityComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import fr.idev.mudserver.domain.world.RoomInstance;
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.component.AttributeComponent;
import fr.idev.mudserver.domain.actor.component.HealthComponent;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.instance.MonsterInstance;

public final class CombatEncounter {

    private final UUID id = UUID.randomUUID();
    private final RoomInstance room;
    private final List<AbstractCharacter> pendingJoiners = new ArrayList<>();
    private final List<InitiativeEntry> order = new ArrayList<>();
    private boolean initiativeRolled;
    private int currentTurnIndex = -1;

    private record InitiativeEntry(AbstractCharacter character, int initiative) {
    }

    public CombatEncounter(RoomInstance room) {
        this.room = room;
    }

    public UUID getId() {
        return id;
    }

    public RoomInstance getRoom() {
        return room;
    }

    public synchronized void joinBeforeInitiative(AbstractCharacter character) {
        if (initiativeRolled) {
            throw new IllegalStateException("Initiative déjà établie, "
                    + character.component(IdentityComponent.class).name + " doit rejoindre via insertLatecomer");
        }
        pendingJoiners.add(character);
    }

    public synchronized boolean isInitiativeRolled() {
        return initiativeRolled;
    }

    public synchronized void establishInitiativeOrder(Function<AbstractCharacter, Integer> initiativeRoller) {
        for (AbstractCharacter character : pendingJoiners) {
            order.add(new InitiativeEntry(character, initiativeRoller.apply(character)));
        }
        pendingJoiners.clear();
        order.sort(this::compareEntries);
        initiativeRolled = true;
        currentTurnIndex = 0;
    }

    public synchronized void insertLatecomer(AbstractCharacter character, int initiative) {
        if (!initiativeRolled) {
            throw new IllegalStateException("L'initiative n'a pas encore été établie pour cet affrontement");
        }
        int insertionIndex = order.size();
        for (int i = 0; i < order.size(); i++) {
            InitiativeEntry existing = order.get(i);
            if (compareEntries(new InitiativeEntry(character, initiative), existing) < 0) {
                insertionIndex = i;
                break;
            }
        }
        order.add(insertionIndex, new InitiativeEntry(character, initiative));
        if (insertionIndex <= currentTurnIndex) {
            currentTurnIndex++;
        }
    }

    public synchronized void remove(AbstractCharacter character) {
        pendingJoiners.remove(character);

        int index = indexOf(character);
        if (index < 0) {
            return;
        }
        order.remove(index);
        if (index < currentTurnIndex) {
            currentTurnIndex--;
        } else if (currentTurnIndex >= order.size() && !order.isEmpty()) {
            currentTurnIndex = 0;
        }
    }

    public synchronized AbstractCharacter currentParticipant() {
        if (order.isEmpty() || currentTurnIndex < 0 || currentTurnIndex >= order.size()) {
            return null;
        }
        return order.get(currentTurnIndex).character();
    }

    public synchronized void advanceTurn() {
        if (order.isEmpty()) {
            return;
        }
        currentTurnIndex = (currentTurnIndex + 1) % order.size();
    }

    public synchronized boolean isOver() {
        if (!initiativeRolled) {
            return false;
        }
        boolean hasMonster = order.stream().anyMatch(entry -> entry.character() instanceof MonsterInstance);
        boolean hasPlayer = order.stream().anyMatch(entry -> entry.character() instanceof CharacterInstance);
        return !hasMonster || !hasPlayer;
    }

    public synchronized List<AbstractCharacter> participants() {
        return order.stream().map(InitiativeEntry::character).toList();
    }

    public synchronized List<CharacterInstance> livingPlayers() {
        return order.stream().map(InitiativeEntry::character)
                .filter(character -> character instanceof CharacterInstance
                        && character.component(HealthComponent.class).currentHealth > 0)
                .map(CharacterInstance.class::cast).toList();
    }

    private int compareEntries(InitiativeEntry a, InitiativeEntry b) {
        int byInitiative = -Integer.compare(a.initiative(), b.initiative());
        if (byInitiative != 0) {
            return byInitiative;
        }
        return -Integer.compare(a.character().component(AttributeComponent.class).modifier(Attribute.DEXTERITY),
                b.character().component(AttributeComponent.class).modifier(Attribute.DEXTERITY));
    }

    private int indexOf(AbstractCharacter character) {
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i).character() == character) {
                return i;
            }
        }
        return -1;
    }
}
