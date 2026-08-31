package app.domain.item;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import app.domain.SpellElement;
import app.domain.actor.AbstractCharacter;
import app.game.combat.CombatFormulas;

public class Item {

    public static final int MAX_ENCHANT = 20;

    private final UUID id;
    private final ItemTemplate template;

    private AbstractCharacter character;
    private EquipmentSlot slot;
    private int enchant;

    public Item(UUID id, ItemTemplate template, AbstractCharacter character, EquipmentSlot slot) {
        this(id, template, character, slot, 0);
    }

    public Item(UUID id, ItemTemplate template, AbstractCharacter character, EquipmentSlot slot, int enchant) {
        this.id = id;
        this.template = Objects.requireNonNull(template);
        this.character = character;
        this.slot = slot;
        setEnchant(enchant);
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
        return CombatFormulas.enchantBonus(template.getPAtk(), enchant, CombatFormulas.ENCHANT_ATK_BONUS_PER_LEVEL);
    }

    public int getMAtk() {
        return CombatFormulas.enchantBonus(template.getMAtk(), enchant, CombatFormulas.ENCHANT_ATK_BONUS_PER_LEVEL);
    }

    public int getPDef() {
        return CombatFormulas.enchantBonus(template.getPDef(), enchant, CombatFormulas.ENCHANT_DEF_BONUS_PER_LEVEL);
    }

    public int getMDef() {
        return CombatFormulas.enchantBonus(template.getMDef(), enchant, CombatFormulas.ENCHANT_DEF_BONUS_PER_LEVEL);
    }

    public int getEnchant() {
        return enchant;
    }

    public void setEnchant(int enchant) {
        this.enchant = Math.max(0, Math.min(MAX_ENCHANT, enchant));
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

    public int getAtkSpd() {
        return template.getAtkSpd();
    }

    public Map<SpellElement, Integer> getElementalResistances() {
        return template.getElementalResistances();
    }

    public ItemGrade getGrade() {
        return template.getGrade();
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
