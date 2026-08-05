package fr.idev.mudserver.domain;

import java.util.Objects;
import java.util.UUID;

import fr.idev.mudserver.domain.actor.GamePlayer;

/**
 * Appartient à exactement une room OU un character, jamais les deux — invariant
 * appliqué par {@link #setCharacter}/{@link #setRoom}, qui se nettoient
 * mutuellement à chaque changement de possesseur.
 */
public class Item {

    private UUID id;
    private UUID templateId;

    // Ne servent qu'à la persistance (ItemDao) — le code applicatif doit utiliser
    // getCharacter()/getRoom(), attachées au chargement par
    // ItemService.loadInventory/warmRoomItems, et tenues à jour par
    // setCharacter()/setRoom() lors des changements de possesseur en jeu.
    private UUID roomId;
    private UUID characterId;

    private EquipmentSlot slot;

    private ItemTemplate template;
    private GamePlayer character;
    private Room room;

    public Item(UUID id, UUID templateId, UUID roomId, UUID characterId, EquipmentSlot slot) {
        this.id = id;
        this.templateId = templateId;
        this.roomId = roomId;
        this.characterId = characterId;
        this.slot = slot;
    }

    public void attachTemplate(ItemTemplate template) {
        this.template = template;
    }

    public ItemTemplate getTemplate() {
        return template;
    }

    public void attachCharacter(GamePlayer character) {
        this.character = character;
    }

    public void attachRoom(Room room) {
        this.room = room;
    }

    public GamePlayer getCharacter() {
        return character;
    }

    public Room getRoom() {
        return room;
    }

    public String getName() {
        return requireTemplate().getName();
    }

    public String getDescription() {
        return requireTemplate().getDescription();
    }

    public ItemType getType() {
        return requireTemplate().getType();
    }

    public int getWeight() {
        return requireTemplate().getWeight();
    }

    public ArmorCategory getArmorCategory() {
        return requireTemplate().getArmorCategory();
    }

    public int getBaseAc() {
        return requireTemplate().getBaseAc();
    }

    public String getDamageDice() {
        return requireTemplate().getDamageDice();
    }

    private ItemTemplate requireTemplate() {
        if (template == null) {
            throw new IllegalStateException("Item " + id + " has no ItemTemplate attached");
        }
        return template;
    }

    public void setCharacter(GamePlayer character) {
        this.character = character;
        this.characterId = character.getId();
        this.room = null;
        this.roomId = null;
        this.slot = null;
    }

    public void setRoom(Room room) {
        this.room = room;
        this.roomId = room.getId();
        this.character = null;
        this.characterId = null;
        this.slot = null;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public void setTemplateId(UUID templateId) {
        this.templateId = templateId;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public UUID getCharacterId() {
        return characterId;
    }

    public EquipmentSlot getSlot() {
        return slot;
    }

    public void setSlot(EquipmentSlot slot) {
        this.slot = slot;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Item other)) {
            return false;
        }
        return Objects.equals(id, other.id) && Objects.equals(templateId, other.templateId)
                && Objects.equals(roomId, other.roomId) && Objects.equals(characterId, other.characterId)
                && slot == other.slot;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, templateId, roomId, characterId, slot);
    }

    @Override
    public String toString() {
        return "Item[id=" + id + ", templateId=" + templateId + ", roomId=" + roomId + ", characterId=" + characterId
                + ", slot=" + slot + "]";
    }
}
