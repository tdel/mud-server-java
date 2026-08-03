package fr.idev.mudserver.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Appartient à exactement une room OU un character, jamais les deux — invariant
 * appliqué par {@code fr.idev.mudserver.game.ItemService}, pas ici.
 */
public class Item {

    private UUID id;
    private UUID templateId;
    private UUID roomId;
    private UUID characterId;
    private EquipmentSlot slot;

    private ItemTemplate template;

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

    private ItemTemplate requireTemplate() {
        if (template == null) {
            throw new IllegalStateException("Item " + id + " has no ItemTemplate attached");
        }
        return template;
    }

    public void assignToCharacter(UUID characterId) {
        this.characterId = characterId;
        this.roomId = null;
        this.slot = null;
    }

    public void assignToRoom(UUID roomId) {
        this.roomId = roomId;
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

    public void setRoomId(UUID roomId) {
        this.roomId = roomId;
    }

    public UUID getCharacterId() {
        return characterId;
    }

    public void setCharacterId(UUID characterId) {
        this.characterId = characterId;
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
