package app.game.engine;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import app.domain.actor.event.PlayerRemovedFromWorld;
import app.domain.Party;
import app.domain.actor.event.CharacterDamaged;
import app.domain.actor.event.CharacterDied;
import app.domain.actor.event.GamePlayerRespawned;
import app.domain.actor.instance.MonsterInstance;
import app.domain.actor.instance.SummonInstance;
import app.domain.world.Corpse;
import app.domain.actor.system.PvPSystem;
import app.domain.world.MapInstance;
import app.network.message.ingame.GamePlayerDefeated;
import app.network.message.ingame.MonsterDefeated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import app.domain.actor.event.PlayerLoadedInWorld;
import app.domain.actor.instance.PlayerInstance;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RegenHealthEngine {

    private static final Logger log = LoggerFactory.getLogger(RegenHealthEngine.class);

    // Période retail L2J (HpTask) : régénération passive toutes les 3 secondes,
    // pas 1 seconde.
    private static final long TICK_INTERVAL_MS = 3_000L;

    // Durée pendant laquelle le cadavre d'un monstre reste consommable.
    private static final long CORPSE_SECONDS = 15;

    private final Map<UUID, PlayerInstance> regenerating = new ConcurrentHashMap<>();

    @EventListener
    void onCharacterDamaged(CharacterDamaged event) {
        if (event.character() instanceof PlayerInstance character) {
            register(character);
        }
    }

    @EventListener
    void onPlayerLoadedInWorld(PlayerLoadedInWorld event) {
        register(event.character());
    }

    public void register(PlayerInstance character) {
        if (isFull(character)) {
            return;
        }
        regenerating.putIfAbsent(character.getId(), character);
    }

    // Le personnage quitte le jeu : PlayerLoadedInWorld réinscrira à son retour la
    // nouvelle instance chargée depuis la base.
    @EventListener
    void onPlayerRemovedFromWorld(PlayerRemovedFromWorld event) {
        regenerating.remove(event.character().getId());
    }

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    void tick() {
        for (PlayerInstance character : regenerating.values()) {
            character.getResourceSystem().regenerate(character.getResourceSystem().healthRegenAmountPerTick(), 0);

            if (isFull(character)) {
                regenerating.remove(character.getId());
            }
        }
    }

    private boolean isFull(PlayerInstance character) {
        return character.getResourceSystem().getCurrentHealth() >= character.getResourceSystem().getMaxHealth();
    }

    @EventListener
    @Transactional
    void onMonsterDied(CharacterDied event) {
        if (!(event.character() instanceof MonsterInstance monster)) {
            return;
        }
        MapInstance map = monster.getMotionSystem().getCurrentMap();
        // Le cadavre reste consommable quelques secondes (Corpse Life Drain).
        map.addCorpse(new Corpse(monster.getId(), monster.getName(), monster.getMotionSystem().getPosition(),
                Instant.now().plusSeconds(CORPSE_SECONDS)));
        map.removeMonster(monster);
        monster.broadcastToMap(new MonsterDefeated(monster.getId(), monster.getName()), null);
        monster.getKnownList().clear();
        log.info("regenhp.monster_removed_from_map monster={} map={}", monster.getName(), map.getName());

        // Victime d'un serviteur : XP et butin reviennent à son maître. Tué par un
        // autre monstre (Curse Discord) : retiré de la carte comme les autres — sinon
        // il resterait, mort, dans la map et les KnownList, et son emplacement de
        // spawn compterait toujours comme occupé — mais personne n'est récompensé.
        PlayerInstance killer = event.killer() instanceof PlayerInstance player
                ? player
                : event.killer() instanceof SummonInstance summon ? summon.getOwner() : null;
        if (killer == null) {
            return;
        }

        Party party = killer.getPartySystem().getParty();
        List<PlayerInstance> eligible = party != null
                ? party.getMembers().stream().filter(
                        member -> member.getMotionSystem().getCurrentMap() == killer.getMotionSystem().getCurrentMap())
                        .toList()
                : List.of(killer);
        double multiplier = party != null ? party.shareMultiplier(eligible.size()) : 1.0;

        monster.getLootSystem().grantLootTo(killer, party, eligible, multiplier);
        monster.getLootSystem().grantXpTo(killer.getName(), eligible, multiplier);
        killer.getCombatSystem().setTarget(null);
        killer.getPvpSystem().addKarma(-PvPSystem.KARMA_LOSS_PER_MONSTER_KILL);
    }

    @EventListener
    void onPlayerDefeated(CharacterDied event) {
        if (!(event.character() instanceof PlayerInstance character)) {
            return;
        }
        regenerating.remove(character.getId());

        MapInstance map = character.getMotionSystem().getCurrentMap();
        character.broadcast(new GamePlayerDefeated(character.getName(), event.killer().getName()), null);
        log.info("regenhp.player_defeated character={} killer={} map={}", character.getName(), event.killer().getName(),
                map.getName());
    }

    @EventListener
    void onGamePlayerRespawned(GamePlayerRespawned event) {
        register(event.character());
    }
}
