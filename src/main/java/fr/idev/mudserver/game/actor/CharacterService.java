package fr.idev.mudserver.game.actor;

import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.event.CharacterDied;
import fr.idev.mudserver.domain.actor.event.CharacterGainedXp;
import fr.idev.mudserver.network.message.ingame.PlayerLeveledUp;
import fr.idev.mudserver.network.message.ingame.XpGained;
import fr.idev.mudserver.persistence.CharacterDao;

/**
 * Réagit à {@link CharacterGainedXp} : persiste l'XP, puis fait franchir à
 * {@code character} autant de paliers de niveau que son XP le permet (une seule
 * mise à mort peut en franchir plusieurs). Le gain de PV par niveau suit la
 * règle 5e « fixe » (alternative au jet de dé) : (dé de vie / 2 + 1) +
 * modificateur de CON, ajouté aux PV max et aux PV courants — pas de soin
 * complet. Cette logique ne peut pas vivre sur {@link GamePlayer} lui-même
 * (simple POJO) puisqu'elle dépend de
 * {@link LevelService}/{@link ClassService}, deux beans Spring.
 *
 * <p>
 * Réagit aussi à {@link CharacterDied} : c'est ici, plutôt que dans
 * {@code CombatService}, que le tueur est crédité de l'XP du monstre et que sa
 * cible est vidée — deux effets qui concernent l'état du personnage joueur, pas
 * la room (voir {@code RoomService#onCharacterDied} pour le pendant « room »).
 */
@Service
public class CharacterService {

    private final CharacterDao characterDao;
    private final LevelService levelService;
    private final ClassService classService;

    public CharacterService(CharacterDao characterDao, LevelService levelService, ClassService classService) {
        this.characterDao = characterDao;
        this.levelService = levelService;
        this.classService = classService;
    }

    @EventListener
    void onCharacterGainedXp(CharacterGainedXp event) {
        GamePlayer character = event.character();
        character.send(new XpGained(event.amount()));
        boolean leveledUp = false;

        while (character.getLevel() < levelService.maxLevel()
                && character.getXp() >= levelService.xpRequiredForLevel(character.getLevel() + 1)) {
            int hitDie = classService.hitDie(character.getCharacterClass());
            int constitutionModifier = character.getModifier(Attribute.CONSTITUTION);
            int hpGain = Math.max(1, hitDie / 2 + 1 + constitutionModifier);

            character.setLevel(character.getLevel() + 1);
            character.setMaxHealth(character.getMaxHealth() + hpGain);
            character.setCurrentHealth(character.getCurrentHealth() + hpGain);
            leveledUp = true;
        }

        characterDao.update(character);

        if (leveledUp) {
            character.getCurrentRoom().broadcast(new PlayerLeveledUp(character.getName(), character.getLevel()), null);
        }
    }

    /**
     * {@code @Order(2)} : ce listener s'exécute après
     * {@code RoomService#onCharacterDied} pour ce même événement, afin que le
     * joueur voie la mort du monstre avant le message d'XP (et une éventuelle
     * montée de niveau) que déclenche {@link GamePlayer#gainXp}.
     */
    @EventListener
    @Order(2)
    void onCharacterDied(CharacterDied event) {
        GamePlayer killer = event.killer();
        killer.gainXp(event.character().getTemplate().getXpReward());
        killer.setTarget(null);
    }
}
