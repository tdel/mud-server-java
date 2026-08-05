package fr.idev.mudserver.domain.actor;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import fr.idev.mudserver.domain.Room;

/**
 * Racine commune à tout ce qui porte des caractéristiques DnD5e et une santé,
 * et peut occuper une {@link Room} : {@link GamePlayer}, {@link GameMonster} et
 * {@link GameNpc}. {@code currentRoom} n'est jamais persisté ni pris en compte
 * par les {@code equals}/{@code hashCode} des sous-classes concrètes — il ne
 * représente que l'état vivant du process, sur le même principe que
 * {@code GamePlayer.connection} (voir sa Javadoc). {@code permits} scelle la
 * hiérarchie à ces trois sous-types : {@code Room.findOccupantByName} peut
 * ainsi retourner un {@code Optional<GameCharacter>} traité par un
 * {@code switch} exhaustif dans {@code Examine}, sans clause {@code default}.
 *
 * <p>
 * {@code GameNpc} hérite {@code attributes}/{@code currentHealth}/
 * {@code maxHealth} sans qu'aucune règle ne les exploite encore — un NPC reste
 * pour l'instant juste un nom et une localisation (voir sa Javadoc).
 */
public abstract sealed class GameCharacter extends GameObject permits GamePlayer, GameMonster, GameNpc {

    private final Map<Attribute, Integer> attributes;
    private int currentHealth;
    private int maxHealth;

    private Room currentRoom;

    protected GameCharacter(UUID id, String name, Map<Attribute, Integer> attributes, int currentHealth,
            int maxHealth) {
        super(id, name);
        this.attributes = new EnumMap<>(attributes);
        this.currentHealth = currentHealth;
        this.maxHealth = maxHealth;
    }

    public int getAttribute(Attribute attribute) {
        return attributes.get(attribute);
    }

    public int getModifier(Attribute attribute) {
        return Math.floorDiv(getAttribute(attribute) - 10, 2);
    }

    public int getArmorClass() {
        return 10 + getModifier(Attribute.DEXTERITY);
    }

    public Map<Attribute, Integer> getAttributes() {
        return Map.copyOf(attributes);
    }

    public int getCurrentHealth() {
        return currentHealth;
    }

    public void setCurrentHealth(int currentHealth) {
        this.currentHealth = currentHealth;
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public void setMaxHealth(int maxHealth) {
        this.maxHealth = maxHealth;
    }

    public Room getCurrentRoom() {
        return currentRoom;
    }

    public void setCurrentRoom(Room currentRoom) {
        this.currentRoom = currentRoom;
    }
}
