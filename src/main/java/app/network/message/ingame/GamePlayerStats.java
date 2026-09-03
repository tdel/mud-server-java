package app.network.message.ingame;

import java.util.List;
import java.util.UUID;

import app.network.OutputJsonMessage;
import app.domain.actor.Attribute;
import app.domain.actor.ModifiedStat;
import app.domain.actor.instance.CharacterInstance;
import app.game.catalog.LevelCatalogHolder;
import app.game.engine.MovementEngine;
import app.network.server.tcpjson.TcpJsonOutput;

public record GamePlayerStats(CharacterInstance character) implements OutputJsonMessage {

    public record AttributeScore(int score, int modifier) {
    }

    public record Payload(UUID id, String name, String gender, int level, String characterClass, int currentHealth,
            int maxHealth, int healthRegenPerSecond, int currentMana, int maxMana, int manaRegenPerSecond, int pAtk,
            int pDef, int mAtk, int mDef, int accuracy, int evasion, int criticalRate, int atkSpd,
            AttributeScore strength, AttributeScore dexterity, AttributeScore constitution, AttributeScore intelligence,
            AttributeScore wit, AttributeScore men, double speed, int xp, int xpForCurrentLevel, int xpForNextLevel) {
    }

    @Override
    public void toJson(TcpJsonOutput output) {
        CharacterInstance c = character;
        int xpForCurrentLevel = LevelCatalogHolder.xpRequiredForLevel(c.getLevel());
        // Au niveau max, il n'y a pas de "niveau suivant" (xpRequiredForLevel lèverait
        // une
        // exception) : on répète le seuil courant pour que le client affiche une barre
        // pleine
        // plutôt que de diviser par zéro (xpForNextLevel - xpForCurrentLevel == 0).
        int xpForNextLevel = c.getLevel() < LevelCatalogHolder.maxLevel()
                ? LevelCatalogHolder.xpRequiredForLevel(c.getLevel() + 1)
                : xpForCurrentLevel;
        output.write("GamePlayerStats", new Payload(c.getId(), c.getName(), c.getAppearanceSystem().getGender().label(),
                c.getLevel(), c.getClassSystem().getCharacterClass().label(), c.getCurrentHealth(), c.getMaxHealth(),
                c.healthRegenAmountPerTick(), c.getCurrentMana(), c.getMaxMana(), c.manaRegenAmountPerTick(),
                c.getStatSystem().getEffective(ModifiedStat.PATK), c.getStatSystem().getEffective(ModifiedStat.PDEF),
                c.getStatSystem().getEffective(ModifiedStat.MATK), c.getStatSystem().getEffective(ModifiedStat.MDEF),
                c.getStatSystem().getEffective(ModifiedStat.ACCURACY),
                c.getStatSystem().getEffective(ModifiedStat.EVASION),
                c.getStatSystem().getEffective(ModifiedStat.PCRIT), c.getStatSystem().getEffective(ModifiedStat.ATKSPD),
                attributeScore(c, Attribute.STR), attributeScore(c, Attribute.DEX), attributeScore(c, Attribute.CON),
                attributeScore(c, Attribute.INT), attributeScore(c, Attribute.WIT), attributeScore(c, Attribute.MEN),
                MovementEngine.unitsPerSecond(c.getMotionSystem().getSpeed()), c.getXp(), xpForCurrentLevel,
                xpForNextLevel), false);
    }

    private static AttributeScore attributeScore(CharacterInstance c, Attribute attribute) {
        return new AttributeScore(c.getAttribute(attribute), c.getModifier(attribute));
    }
}
