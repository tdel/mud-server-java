package app.game.engine;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import app.domain.actor.event.CharacterBeginAttack;
import app.domain.actor.event.CharacterDied;
import app.domain.actor.instance.PlayerInstance;
import app.game.WorldInstanceService;

@Component
public class PvpEngine {

    private static final Logger log = LoggerFactory.getLogger(PvpEngine.class);

    private static final long TICK_INTERVAL_MS = 5_000L;

    private final WorldInstanceService worldInstanceService;

    public PvpEngine(WorldInstanceService worldInstanceService) {
        this.worldInstanceService = worldInstanceService;
    }

    // Un joueur sans karma qui attaque un autre joueur devient (ou reste) flaggé
    // PvP ; attaquer un joueur qui a du karma (un PK) ne flagge pas l'attaquant.
    // CharacterBeginAttack est publié par CombatSystem et SkillSystem, donc cette
    // règle couvre aussi bien le corps-à-corps que les sorts offensifs.
    @EventListener
    void onCharacterBeginAttack(CharacterBeginAttack event) {
        if (!(event.attacker() instanceof PlayerInstance attacker)
                || !(event.defender() instanceof PlayerInstance defender)) {
            return;
        }
        if (defender.getPvpSystem().getKarma() > 0) {
            return;
        }
        attacker.getPvpSystem().flagPvp();
    }

    // Filtre le miroir de
    // MonsterAiEngine/CharacterPersistenceListener.onCharacterDied
    // (victime ET tueur joueurs, pas de monstre) : les listeners partagent
    // CharacterDied sans dépendance d'ordre entre eux (cf. CLAUDE.md).
    @EventListener
    void onCharacterDied(CharacterDied event) {
        if (!(event.character() instanceof PlayerInstance victim)
                || !(event.killer() instanceof PlayerInstance killer)) {
            return;
        }
        boolean victimWasPvpFlagged = victim.getPvpSystem().isPvpFlagged();
        killer.getPvpSystem().resolvePlayerKill(victim);
        log.info("pvp.player_killed victim={} killer={} victimWasPvpFlagged={} killerKarma={}", victim.getName(),
                killer.getName(), victimWasPvpFlagged, killer.getPvpSystem().getKarma());
    }

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    void tick() {
        if (!worldInstanceService.isDefaultWorldMaterialized()) {
            return;
        }
        expireFlags(worldInstanceService.getDefaultInstance().onlineCharacters());
    }

    void expireFlags(Collection<PlayerInstance> onlineCharacters) {
        for (PlayerInstance character : onlineCharacters) {
            if (character.getPvpSystem().isPvpFlagExpired()) {
                character.getPvpSystem().clearPvpFlag();
                log.info("pvp.flag_expired character={}", character.getId());
            }
        }
    }
}
