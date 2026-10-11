package app.game.engine;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import app.domain.ActiveSkill;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.GamePlayerRespawned;
import app.domain.actor.event.PlayerRemovedFromWorld;
import app.domain.actor.instance.PlayerInstance;
import app.network.message.ingame.CharacterResurrected;
import app.network.message.ingame.PlayerResurrected;
import app.network.message.ingame.ResurrectionOffered;

// Resurrection / Mass Resurrection : comme dans L2, le joueur mort reçoit une
// proposition qu'il accepte (resurrect-accept) pour revenir à la vie sur place,
// au lieu de réapparaître en ville (respawn). La proposition expire au bout
// d'une minute ou s'il réapparaît entre-temps. Les PV/PM rendus croissent avec
// le level du sort.
@Component
public class ResurrectionEngine {

    private static final Logger log = LoggerFactory.getLogger(ResurrectionEngine.class);

    private static final int OFFER_SECONDS = 60;

    private final Map<UUID, Offer> offers = new ConcurrentHashMap<>();

    public void offer(PlayerInstance caster, PlayerInstance dead, ActiveSkill activeSkill, int level) {
        offers.put(dead.getId(),
                new Offer(caster.getName(), level, System.currentTimeMillis() + OFFER_SECONDS * 1000L));
        dead.send(new ResurrectionOffered(caster.getId(), caster.getName(), activeSkill.name(), OFFER_SECONDS));
        log.info("resurrection.offered caster={} dead={} level={}", caster.getName(), dead.getName(), level);
    }

    // false : aucune proposition valable (expirée, ou joueur vivant).
    public boolean accept(PlayerInstance player) {
        Offer offer = offers.remove(player.getId());
        if (offer == null || System.currentTimeMillis() > offer.expiresAtMillis()
                || player.getResourceSystem().getCurrentHealth() > 0) {
            return false;
        }
        // Level 1 : 30 % des PV, puis +10 % par level (Mass Resurrection : 30 %).
        int percent = Math.min(70, 20 + offer.level() * 10);
        player.getResourceSystem()
                .setCurrentHealth(Math.max(1, player.getResourceSystem().getMaxHealth() * percent / 100));
        player.getResourceSystem().setCurrentMana(player.getResourceSystem().getMaxMana() * percent / 200);
        player.send(new PlayerResurrected(offer.casterName(), player.getMotionSystem().getPosition().x(),
                player.getMotionSystem().getPosition().y(), player.getResourceSystem().getCurrentHealth(),
                player.getResourceSystem().getMaxHealth(), player.getResourceSystem().getCurrentMana(),
                player.getResourceSystem().getMaxMana()));
        player.broadcast(
                new CharacterResurrected(player.getId(), player.getName(),
                        player.getResourceSystem().getCurrentHealth(), player.getResourceSystem().getMaxHealth()),
                player);
        DomainEventPublisher.publish(new GamePlayerRespawned(player));
        log.info("resurrection.accepted character={} caster={} healthPercent={}", player.getName(), offer.casterName(),
                percent);
        return true;
    }

    @EventListener
    void onGamePlayerRespawned(GamePlayerRespawned event) {
        offers.remove(event.character().getId());
    }

    @EventListener
    void onPlayerRemovedFromWorld(PlayerRemovedFromWorld event) {
        offers.remove(event.character().getId());
    }

    private record Offer(String casterName, int level, long expiresAtMillis) {
    }
}
