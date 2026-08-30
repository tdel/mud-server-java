package app.domain.item;

import java.util.Objects;
import java.util.UUID;

import app.domain.actor.AbstractCharacter;

public class Item {

    private final UUID id;
    private final ItemTemplate template;

    private AbstractCharacter character;
    private EquipmentSlot slot;

    public Item(UUID id, ItemTemplate template, AbstractCharacter character, EquipmentSlot slot) {
        this.id = id;
        this.template = Objects.requireNonNull(template);
        this.character = character;
        this.slot = slot;
    }

    public ItemTemplate getTemplate() {
        return template;
    }

    public AbstractCharacter getCharacter() {
        return character;
    }

    public String getName() {
        return template.getName();
    }

    public String getDescription() {
        return template.getDescription();
    }

    public ItemType getType() {
        return template.getType();
    }

    public int getWeight() {
        return template.getWeight();
    }

    public ArmorCategory getArmorCategory() {
        return template.getArmorCategory();
    }

    public int getPAtk() {
        return template.getPAtk();
    }

    public int getMAtk() {
        return template.getMAtk();
    }

    public int getPDef() {
        return template.getPDef();
    }

    public int getMDef() {
        return template.getMDef();
    }

    public int getAccuracyBonus() {
        return template.getAccuracyBonus();
    }

    public int getEvasionBonus() {
        return template.getEvasionBonus();
    }

    public int getCritBonus() {
        return template.getCritBonus();
    }

    public Rarity getRarity() {
        return template.getRarity();
    }

    public void setCharacter(AbstractCharacter character) {
        this.character = character;
        this.slot = null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTemplateId() {
        return template.getId();
    }

    public UUID getCharacterId() {
        return character == null ? null : character.getId();
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
        return Objects.equals(id, other.id) && Objects.equals(getTemplateId(), other.getTemplateId())
                && Objects.equals(getCharacterId(), other.getCharacterId()) && slot == other.slot;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, getTemplateId(), getCharacterId(), slot);
    }

    @Override
    public String toString() {
        return "Item[id=" + id + ", templateId=" + getTemplateId() + ", characterId=" + getCharacterId() + ", slot="
                + slot + "]";
    }
}
