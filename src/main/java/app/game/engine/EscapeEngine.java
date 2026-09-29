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
import app.domain.actor.event.PlayerRemovedFromWorld;
import app.domain.actor.instance.PlayerInstance;
import app.domain.map.Position;
import app.domain.world.MapInstance;
import app.network.message.ingame.CharacterTeleporting;
import app.network.message.ingame.MapEnter;
import app.network.message.ingame.MapView;

// Résolution du sort Teleport (Scroll of Escape) : à la fin de l'incantation
// (SkillCastEngine.resolveCast), le lanceur devient intouchable
// (CombatSystem.setTeleporting) le temps de l'animation d'ascension côté client
// (CharacterTeleporting), puis est déplacé en un point au hasard de la ville la
// plus proche (WorldInstance.nearestTown / MapInstance.randomTownPosition).
@Component
public class EscapeEngine {

    private static final Logger log = LoggerFactory.getLogger(EscapeEngine.class);

    private static final long TICK_INTERVAL_MS = 100L;
    // Durée de l'ascension blanche jouée par le client avant le changement de
    // carte.
    static final int ASCENSION_DELAY_MS = 1600;

    private final Map<UUID, PendingEscape> pending = new ConcurrentHashMap<>();
    private final MovementEngine movementEngine;

    public EscapeEngine(MovementEngine movementEngine) {
        this.movementEngine = movementEngine;
    }

    public boolean isPending(AbstractCharacter character) {
        return pending.containsKey(character.getId());
    }

    public void begin(AbstractCharacter caster) {
        if (!(caster instanceof PlayerInstance player)) {
            return;
        }
        MapInstance from = player.getMotionSystem().getCurrentMap();
        MapInstance destination = player.getWorldInstance().nearestTown(from).orElse(null);
        if (destination == null) {
            log.warn("escape.no_town character={} map={}", player.getId(), from.getName());
            return;
        }
        Position arrival = destination.randomTownPosition();
        movementEngine.stopMovement(player);
        player.clearCombatTarget();
        player.getCombatSystem().setTeleporting(true);
        pending.put(player.getId(),
                new PendingEscape(player, destination, arrival, System.nanoTime() + ASCENSION_DELAY_MS * 1_000_000L));
        log.info("escape.started character={} from={} to={} arrival={}", player.getId(), from.getName(),
                destination.getName(), arrival);
        player.broadcast(
                new CharacterTeleporting(player.getId(), player.getName(), destination.getName(), ASCENSION_DELAY_MS),
                null);
    }

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    void tick() {
        long now = System.nanoTime();
        for (PendingEscape escape : pending.values()) {
            if (now < escape.teleportAtNanos()) {
                continue;
            }
            pending.remove(escape.player().getId());
            try {
                teleport(escape);
            } catch (Exception e) {
                log.error("escape.teleport_failed character={}", escape.player().getId(), e);
            } finally {
                escape.player().getCombatSystem().setTeleporting(false);
            }
        }
    }

    private void teleport(PendingEscape escape) {
        PlayerInstance player = escape.player();
        MapInstance current = player.getMotionSystem().getCurrentMap();
        if (current == null || !current.isPresent(player)) {
            log.debug("escape.dropped character={} reason=left_map", player.getId());
            return;
        }
        // Même garde que Portal.java : aucun déplacement en cours ne doit survivre au
        // changement de carte.
        movementEngine.stopMovement(player);
        player.getMotionSystem().moveToMap(escape.destination(), escape.arrival());
        player.send(new MapView(player.getMotionSystem().getCurrentMap()));
        player.send(new MapEnter(player));
        log.info("escape.completed character={} map={} position={}", player.getId(), escape.destination().getName(),
                escape.arrival());
    }

    @EventListener
    void onPlayerRemovedFromWorld(PlayerRemovedFromWorld event) {
        if (pending.remove(event.character().getId()) != null) {
            event.character().getCombatSystem().setTeleporting(false);
        }
    }

    private record PendingEscape(PlayerInstance player, MapInstance destination, Position arrival,
            long teleportAtNanos) {
    }
}
