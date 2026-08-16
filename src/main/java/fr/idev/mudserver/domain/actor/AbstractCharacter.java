package fr.idev.mudserver.domain.actor;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import fr.idev.mudserver.domain.actor.component.AttributeComponent;
import fr.idev.mudserver.domain.actor.component.CombatComponent;
import fr.idev.mudserver.domain.actor.component.MovementComponent;
import fr.idev.mudserver.domain.actor.component.NetworkComponent;
import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.world.RoomInstance;
import fr.idev.mudserver.domain.combat.ActionEconomy;
import fr.idev.mudserver.domain.combat.CombatEncounter;
import fr.idev.mudserver.network.OutputMessage;

public abstract class AbstractCharacter extends AbstractObject {

    private volatile RoomInstance currentRoom;
    private volatile HexCoordinate position;
    private volatile CombatEncounter encounter;
    private final ActionEconomy actionEconomy = new ActionEconomy();

    protected AbstractCharacter(UUID id, String name, Map<Attribute, Integer> attributes, int currentHealth,
            int maxHealth, int speed) {
        super(id, name);
        this.attachComponent(new AttributeComponent(new EnumMap<>(attributes)));
        this.attachComponent(new CombatComponent(currentHealth, maxHealth, null));
        this.attachComponent(new MovementComponent(speed));
    }

    public RoomInstance getCurrentRoom() {
        return currentRoom;
    }

    public void setCurrentRoom(RoomInstance currentRoom) {
        this.currentRoom = currentRoom;
    }

    public HexCoordinate getPosition() {
        return position;
    }

    public void setPosition(HexCoordinate position) {
        this.position = position;
    }

    public boolean isInCombat() {
        return encounter != null;
    }

    public CombatEncounter getEncounter() {
        return encounter;
    }

    public void setEncounter(CombatEncounter encounter) {
        this.encounter = encounter;
    }

    public ActionEconomy getActionEconomy() {
        return actionEconomy;
    }

    public boolean onEnteredCell(HexCoordinate cell) {
        return false;
    }

    public void send(OutputMessage message) {
        findComponent(NetworkComponent.class)
                .ifPresent(networkComponent -> networkComponent.connection().send(message));
    }
}
