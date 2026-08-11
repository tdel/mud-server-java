package fr.idev.mudserver.domain.actor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import fr.idev.mudserver.domain.RoomInstance;

public final class CombatEncounter {

    private final RoomInstance room;
    private final List<GameCharacter> pendingJoiners = new ArrayList<>();
    private final List<InitiativeEntry> order = new ArrayList<>();
    private boolean initiativeRolled;
    private int currentTurnIndex = -1;

    private record InitiativeEntry(GameCharacter character, int initiative) {
    }

    public CombatEncounter(RoomInstance room) {
        this.room = room;
    }

    public RoomInstance getRoom() {
        return room;
    }

    public synchronized void joinBeforeInitiative(GameCharacter character) {
        if (initiativeRolled) {
            throw new IllegalStateException(
                    "Initiative déjà établie, " + character.getName() + " doit rejoindre via insertLatecomer");
        }
        pendingJoiners.add(character);
    }

    public synchronized boolean isInitiativeRolled() {
        return initiativeRolled;
    }

    public synchronized void establishInitiativeOrder(Function<GameCharacter, Integer> initiativeRoller) {
        for (GameCharacter character : pendingJoiners) {
            order.add(new InitiativeEntry(character, initiativeRoller.apply(character)));
        }
        pendingJoiners.clear();
        order.sort(this::compareEntries);
        initiativeRolled = true;
        currentTurnIndex = 0;
    }

    public synchronized void insertLatecomer(GameCharacter character, int initiative) {
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

    public synchronized void remove(GameCharacter character) {
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

    public synchronized GameCharacter currentParticipant() {
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
        boolean hasMonster = order.stream().anyMatch(entry -> entry.character() instanceof GameMonster);
        boolean hasPlayer = order.stream().anyMatch(entry -> entry.character() instanceof GamePlayer);
        return !hasMonster || !hasPlayer;
    }

    public synchronized List<GameCharacter> participants() {
        return order.stream().map(InitiativeEntry::character).toList();
    }

    public synchronized List<GamePlayer> livingPlayers() {
        return order.stream().map(InitiativeEntry::character)
                .filter(character -> character instanceof GamePlayer && character.getCurrentHealth() > 0)
                .map(GamePlayer.class::cast).toList();
    }

    private int compareEntries(InitiativeEntry a, InitiativeEntry b) {
        int byInitiative = -Integer.compare(a.initiative(), b.initiative());
        if (byInitiative != 0) {
            return byInitiative;
        }
        return -Integer.compare(a.character().getModifier(Attribute.DEXTERITY),
                b.character().getModifier(Attribute.DEXTERITY));
    }

    private int indexOf(GameCharacter character) {
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i).character() == character) {
                return i;
            }
        }
        return -1;
    }
}
