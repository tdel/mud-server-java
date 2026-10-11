package app.game.engine;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import app.domain.actor.AbstractCharacter;
import app.domain.actor.event.CharacterChoseSubclass;
import app.domain.actor.event.CharacterEffectApplied;
import app.domain.actor.event.CharacterEffectExpired;
import app.domain.actor.event.CharacterLearnedPassiveSkill;
import app.domain.actor.event.CharacterLeveledUp;
import app.domain.actor.event.GamePlayerEquippedItem;
import app.domain.actor.event.GamePlayerUnequippedItem;
import app.domain.actor.event.PlayerRemovedFromWorld;
import app.domain.actor.instance.PlayerInstance;
import app.network.message.ingame.GamePlayerStats;

// Pousse au joueur sa fiche GamePlayerStats dès que ses stats changent : effet
// posé ou levé (buff, debuff, pénalité de grade), équipement, niveau, compétence
// passive, sous-classe — sans attendre qu'il la redemande ("stats"). Chaque
// changement marque le joueur ; le tick suivant envoie une seule fiche par
// rafale (une mort fait tomber tous les buffs d'un coup) et toujours après le
// recalcul (GamePlayerEquippedItem est publié avant StatSystem.recomputeStats).
@Component
public class PlayerStatsEngine {

    private static final Logger log = LoggerFactory.getLogger(PlayerStatsEngine.class);

    private static final long FLUSH_INTERVAL_MS = 200L;

    private final Map<UUID, PlayerInstance> changed = new ConcurrentHashMap<>();

    @EventListener
    void onCharacterEffectApplied(CharacterEffectApplied event) {
        markChanged(event.character());
    }

    @EventListener
    void onCharacterEffectExpired(CharacterEffectExpired event) {
        markChanged(event.character());
    }

    @EventListener
    void onGamePlayerEquippedItem(GamePlayerEquippedItem event) {
        markChanged(event.character());
    }

    @EventListener
    void onGamePlayerUnequippedItem(GamePlayerUnequippedItem event) {
        markChanged(event.character());
    }

    @EventListener
    void onCharacterLeveledUp(CharacterLeveledUp event) {
        markChanged(event.character());
    }

    @EventListener
    void onCharacterLearnedPassiveSkill(CharacterLearnedPassiveSkill event) {
        markChanged(event.character());
    }

    @EventListener
    void onCharacterChoseSubclass(CharacterChoseSubclass event) {
        markChanged(event.character());
    }

    @EventListener
    void onPlayerRemovedFromWorld(PlayerRemovedFromWorld event) {
        changed.remove(event.character().getId());
    }

    // Monstres et PNJ n'ont pas de fiche : seuls les joueurs sont suivis.
    private void markChanged(AbstractCharacter character) {
        if (character instanceof PlayerInstance player) {
            changed.put(player.getId(), player);
        }
    }

    @Scheduled(fixedRate = FLUSH_INTERVAL_MS)
    void flush() {
        for (PlayerInstance player : changed.values()) {
            if (!changed.remove(player.getId(), player)) {
                continue;
            }
            try {
                player.send(new GamePlayerStats(player));
                log.debug("stats.pushed character={}", player.getId());
            } catch (Exception e) {
                log.error("stats.push_failed character={}", player.getId(), e);
            }
        }
    }
}
