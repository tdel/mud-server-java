package fr.idev.mudserver.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import fr.idev.mudserver.network.Connection;

/**
 * {@code connection} n'est jamais persisté ni pris en compte par
 * {@link #equals}/{@link #hashCode} : il ne représente rien en base, seulement
 * la session réseau qui porte ce personnage tant qu'il est en jeu (voir
 * {@code GameWorld.enterWorld}). Un personnage fraîchement chargé depuis
 * {@code CharacterDao} n'a pas encore de connexion tant qu'il n'a pas rejoint
 * le monde.
 */
public class Character {

    private UUID id;
    private UUID accountId;
    private String name;
    private UUID currentRoomId;
    private Race race;
    private int currentHealth;
    private int maxHealth;
    private int currentMana;
    private int maxMana;
    private int strength;
    private int dexterity;
    private int constitution;
    private int intelligence;
    private int wisdom;
    private int charisma;

    private Connection connection;
    private final List<Item> inventory = new CopyOnWriteArrayList<>();

    public Character(UUID id, UUID accountId, String name, UUID currentRoomId, Race race, int currentHealth,
            int maxHealth, int currentMana, int maxMana, int strength, int dexterity, int constitution,
            int intelligence, int wisdom, int charisma) {
        this.id = id;
        this.accountId = accountId;
        this.name = name;
        this.currentRoomId = currentRoomId;
        this.race = race;
        this.currentHealth = currentHealth;
        this.maxHealth = maxHealth;
        this.currentMana = currentMana;
        this.maxMana = maxMana;
        this.strength = strength;
        this.dexterity = dexterity;
        this.constitution = constitution;
        this.intelligence = intelligence;
        this.wisdom = wisdom;
        this.charisma = charisma;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getCurrentRoomId() {
        return currentRoomId;
    }

    public void setCurrentRoomId(UUID currentRoomId) {
        this.currentRoomId = currentRoomId;
    }

    public Race getRace() {
        return race;
    }

    public void setRace(Race race) {
        this.race = race;
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

    public int getCurrentMana() {
        return currentMana;
    }

    public void setCurrentMana(int currentMana) {
        this.currentMana = currentMana;
    }

    public int getMaxMana() {
        return maxMana;
    }

    public void setMaxMana(int maxMana) {
        this.maxMana = maxMana;
    }

    public int getStrength() {
        return strength;
    }

    public void setStrength(int strength) {
        this.strength = strength;
    }

    public int getDexterity() {
        return dexterity;
    }

    public void setDexterity(int dexterity) {
        this.dexterity = dexterity;
    }

    public int getConstitution() {
        return constitution;
    }

    public void setConstitution(int constitution) {
        this.constitution = constitution;
    }

    public int getIntelligence() {
        return intelligence;
    }

    public void setIntelligence(int intelligence) {
        this.intelligence = intelligence;
    }

    public int getWisdom() {
        return wisdom;
    }

    public void setWisdom(int wisdom) {
        this.wisdom = wisdom;
    }

    public int getCharisma() {
        return charisma;
    }

    public void setCharisma(int charisma) {
        this.charisma = charisma;
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public List<Item> getInventory() {
        return List.copyOf(inventory);
    }

    public List<Item> getCarriedItems() {
        return inventory.stream().filter(item -> item.getSlot() == null).toList();
    }

    public List<Item> getEquippedItems() {
        return inventory.stream().filter(item -> item.getSlot() != null).toList();
    }

    public void addItem(Item item) {
        inventory.add(item);
    }

    public void removeItem(Item item) {
        inventory.remove(item);
    }

    public void setInventory(List<Item> items) {
        inventory.clear();
        inventory.addAll(items);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Character other)) {
            return false;
        }
        return currentHealth == other.currentHealth && maxHealth == other.maxHealth && currentMana == other.currentMana
                && maxMana == other.maxMana && strength == other.strength && dexterity == other.dexterity
                && constitution == other.constitution && intelligence == other.intelligence && wisdom == other.wisdom
                && charisma == other.charisma && Objects.equals(id, other.id)
                && Objects.equals(accountId, other.accountId) && Objects.equals(name, other.name)
                && Objects.equals(currentRoomId, other.currentRoomId) && race == other.race;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, accountId, name, currentRoomId, race, currentHealth, maxHealth, currentMana, maxMana,
                strength, dexterity, constitution, intelligence, wisdom, charisma);
    }

    @Override
    public String toString() {
        return "Character[id=" + id + ", accountId=" + accountId + ", name=" + name + ", currentRoomId=" + currentRoomId
                + ", race=" + race + ", currentHealth=" + currentHealth + ", maxHealth=" + maxHealth + ", currentMana="
                + currentMana + ", maxMana=" + maxMana + ", strength=" + strength + ", dexterity=" + dexterity
                + ", constitution=" + constitution + ", intelligence=" + intelligence + ", wisdom=" + wisdom
                + ", charisma=" + charisma + "]";
    }
}
