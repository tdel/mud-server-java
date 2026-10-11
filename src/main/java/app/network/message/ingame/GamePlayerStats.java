package app.network.message.ingame;

import java.util.List;
import java.util.UUID;

import app.network.OutputJsonMessage;
import app.domain.actor.Attribute;
import app.domain.actor.ModifiedStat;
import app.domain.actor.Subclass;
import app.domain.actor.instance.PlayerInstance;
import app.domain.item.ItemGrade;
import app.game.catalog.LevelCatalogHolder;
import app.game.engine.MovementEngine;
import app.network.server.tcpjson.TcpJsonOutput;

public record GamePlayerStats(PlayerInstance character) implements OutputJsonMessage {

    public record AttributeScore(int score, int modifier) {
    }

    // activeSoulshotGrade/activeSpiritshotGrade : null si l'auto-use est désactivé
    // pour cette
    // catégorie (même convention que ShotGradeChanged.grade) — sans ce champ, un
    // client qui
    // se reconnecte n'a aucun moyen de savoir que le toggle persisté côté serveur
    // (voir
    // CharacterDao) est actif tant qu'il n'a pas reçu un ShotUsed/ShotGradeChanged.
    // criticalRate/magicCriticalRate : taux L2 en millièmes (88 = 8,8 %) ;
    // runSpeed : vitesse de course L2 affichée, speed : la même en cases/s.
    // subclass : profession actuelle (nom d'enum, null avant le level 20) ;
    // className : son nom L2 (Sorcerer...), à défaut celui de la classe de base.
    public record Payload(UUID id, String name, String title, String gender, int level, String characterClass,
            Subclass subclass, String className, int currentHealth, int maxHealth, int healthRegenPerSecond,
            int currentMana, int maxMana, int manaRegenPerSecond, int pAtk, int pDef, int mAtk, int mDef, int accuracy,
            int evasion, int criticalRate, int magicCriticalRate, int atkSpd, int castSpd, int runSpeed,
            AttributeScore strength, AttributeScore dexterity, AttributeScore constitution, AttributeScore intelligence,
            AttributeScore wit, AttributeScore men, double speed, int xp, int xpForCurrentLevel, int xpForNextLevel,
            ItemGrade activeSoulshotGrade, ItemGrade activeSpiritshotGrade, int karma, int pkCount, int pvpCount,
            boolean pvpFlagged, List<ActiveEffectView> effects) {
    }

    @Override
    public void toJson(TcpJsonOutput output) {
        PlayerInstance c = character;
        int xpForCurrentLevel = LevelCatalogHolder.xpRequiredForLevel(c.getLevelingSystem().getLevel());
        // Au niveau max, il n'y a pas de "niveau suivant" (xpRequiredForLevel lèverait
        // une
        // exception) : on répète le seuil courant pour que le client affiche une barre
        // pleine
        // plutôt que de diviser par zéro (xpForNextLevel - xpForCurrentLevel == 0).
        int xpForNextLevel = c.getLevelingSystem().getLevel() < LevelCatalogHolder.maxLevel()
                ? LevelCatalogHolder.xpRequiredForLevel(c.getLevelingSystem().getLevel() + 1)
                : xpForCurrentLevel;
        output.write("GamePlayerStats", new Payload(c.getId(), c.getName(), c.getTitle(),
                c.getAppearanceSystem().getGender().label(), c.getLevelingSystem().getLevel(),
                c.getClassSystem().getCharacterClass().label(), c.getClassSystem().getCurrentSubclass(),
                c.getClassSystem().currentClassLabel(), c.getResourceSystem().getCurrentHealth(),
                c.getResourceSystem().getMaxHealth(), c.getResourceSystem().healthRegenAmountPerTick(),
                c.getResourceSystem().getCurrentMana(), c.getResourceSystem().getMaxMana(),
                c.getResourceSystem().manaRegenAmountPerTick(), c.getStatSystem().getEffective(ModifiedStat.PATK),
                c.getStatSystem().getEffective(ModifiedStat.PDEF), c.getStatSystem().getEffective(ModifiedStat.MATK),
                c.getStatSystem().getEffective(ModifiedStat.MDEF),
                c.getStatSystem().getEffective(ModifiedStat.ACCURACY),
                c.getStatSystem().getEffective(ModifiedStat.EVASION),
                c.getStatSystem().getEffective(ModifiedStat.PCRIT), c.getStatSystem().getEffective(ModifiedStat.MCRIT),
                c.getStatSystem().getEffective(ModifiedStat.ATKSPD),
                c.getStatSystem().getEffective(ModifiedStat.CASTSPD), c.getMotionSystem().getSpeed(),
                attributeScore(c, Attribute.STR), attributeScore(c, Attribute.DEX), attributeScore(c, Attribute.CON),
                attributeScore(c, Attribute.INT), attributeScore(c, Attribute.WIT), attributeScore(c, Attribute.MEN),
                MovementEngine.unitsPerSecond(c.getMotionSystem().getSpeed()), c.getLevelingSystem().getXp(),
                xpForCurrentLevel, xpForNextLevel, c.getInventorySystem().getActiveSoulshotGrade(),
                c.getInventorySystem().getActiveSpiritshotGrade(), c.getPvpSystem().getKarma(),
                c.getPvpSystem().getPkCount(), c.getPvpSystem().getPvpCount(), c.getPvpSystem().isPvpFlagged(),
                ActiveEffectView.listOf(c)));
    }

    private static AttributeScore attributeScore(PlayerInstance c, Attribute attribute) {
        return new AttributeScore(c.getAttributeSystem().getAttribute(attribute),
                c.getAttributeSystem().getModifier(attribute));
    }
}
