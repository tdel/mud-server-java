package app.game.engine;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import app.domain.actor.event.PlayerRemovedFromWorld;
import app.domain.Party;
import app.domain.actor.AbstractCharacter;
import app.domain.ActiveEffect;
import app.domain.PeriodicDamage;
import app.domain.actor.event.CharacterBeginAttack;
import app.domain.actor.event.CharacterDied;
import app.domain.actor.event.CharacterEffectApplied;
import app.domain.actor.event.CharacterStartedMoving;
import app.domain.actor.event.SkillCastBegin;
import app.domain.actor.event.CharacterEffectExpired;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.PlayerLoadedInWorld;
import app.domain.actor.instance.PlayerInstance;
import app.network.message.ingame.ActiveEffectView;
import app.network.message.ingame.EffectApplied;
import app.network.message.ingame.EffectDamage;
import app.network.message.ingame.RegenTick;
import app.network.message.ingame.ToggleDeactivated;
import app.network.message.ingame.PartyMemberEffectExpired;
import app.network.message.ingame.SkillModifierExpired;

@Component
public class ActiveEffectEngine {

    private static final Logger log = LoggerFactory.getLogger(ActiveEffectEngine.class);

    private static final long TICK_INTERVAL_MS = 1_000L;

    private static final long TOGGLE_DRAIN_PERIOD_MS = 5_000L;

    private final Map<UUID, AbstractCharacter> tracked = new ConcurrentHashMap<>();
    private final Map<ToggleKey, Instant> nextToggleDrainAt = new ConcurrentHashMap<>();

    private record ToggleKey(UUID characterId, UUID skillId) {
    }

    // Suivi d'expiration + état diffusé à tous ceux qui voient le porteur (icône
    // sous ses jauges) — le lanceur a déjà son SkillModifierAnnounced.
    @EventListener
    void onCharacterEffectApplied(CharacterEffectApplied event) {
        AbstractCharacter character = event.character();
        register(character);
        character.broadcast(new EffectApplied(character.getId(), character.getName(),
                ActiveEffectView.of(event.effect(), Instant.now())), null);
    }

    @EventListener
    void onCharacterEffectExpired(CharacterEffectExpired event) {
        event.character().broadcast(new SkillModifierExpired(event.character().getId(), event.character().getName(),
                event.effect().skillName()), null);

        if (event.character() instanceof PlayerInstance character) {
            Party party = character.getPartySystem().getParty();
            if (party != null) {
                party.broadcast(new PartyMemberEffectExpired(character.getId(), character.getName(),
                        event.effect().skillName()), character);
            }
        }
    }

    @EventListener
    void onPlayerLoadedInWorld(PlayerLoadedInWorld event) {
        if (!event.character().getEffectsSystem().isEmpty()) {
            register(event.character());
        }
    }

    // Poison : chaque période échue retire son montant de PV à la cible, crédité à
    // son lanceur (XP/loot si le poison achève un monstre). Un poison ne survit
    // pas à la mort de sa cible.
    private void applyPeriodicDamage(AbstractCharacter character, Instant now) {
        if (character.getResourceSystem().getCurrentHealth() <= 0) {
            character.getEffectsSystem().removePeriodicDamage();
            return;
        }
        for (ActiveEffect effect : character.getEffectsSystem().duePeriodicDamage(now)) {
            PeriodicDamage damage = effect.periodicDamage();
            if (character.getResourceSystem().getCurrentHealth() <= 0) {
                break;
            }
            boolean defeated = character.takeDamage(damage.amount(), damage.source());
            character.broadcast(new EffectDamage(character.getId(), character.getName(), effect.skillName(),
                    damage.amount(), character.getResourceSystem().getCurrentHealth(),
                    character.getResourceSystem().getMaxHealth(), defeated, damage.source().getId()), null);
            log.debug("effect.periodic_damage character={} effect={} amount={} defeated={}", character.getId(),
                    effect.skillName(), damage.amount(), defeated);
            if (defeated) {
                character.getEffectsSystem().removePeriodicDamage();
            }
        }
    }

    // Greater Heal / Greater Group Heal : soin régulier tant que l'effet dure.
    private void applyPeriodicHeal(AbstractCharacter character, Instant now) {
        if (character.getResourceSystem().getCurrentHealth() <= 0) {
            return;
        }
        for (ActiveEffect effect : character.getEffectsSystem().duePeriodicHeal(now)) {
            int healed = character.getResourceSystem().heal(effect.periodicHeal().amount());
            if (healed > 0 && character instanceof PlayerInstance player) {
                player.send(new RegenTick(healed, 0, player.getResourceSystem().getCurrentHealth(),
                        player.getResourceSystem().getMaxHealth(), player.getResourceSystem().getCurrentMana(),
                        player.getResourceSystem().getMaxMana()));
            }
        }
    }

    // Toggles (Vicious Stance...) : toggleMpPerTick PM toutes les 5 s ; à court de
    // PM, le toggle se coupe (message système de L2).
    private void drainToggles(AbstractCharacter character, Instant now) {
        List<ActiveEffect> toggles = character.getEffectsSystem().toggles();
        // Toggle coupé par le joueur ou par la mort : oublie son échéance, sinon la
        // clé reste à vie et une réactivation draine des PM immédiatement.
        nextToggleDrainAt.keySet().removeIf(key -> key.characterId().equals(character.getId())
                && toggles.stream().noneMatch(toggle -> toggle.skillId().equals(key.skillId())));
        for (ActiveEffect toggle : toggles) {
            ToggleKey key = new ToggleKey(character.getId(), toggle.skillId());
            Instant next = nextToggleDrainAt.computeIfAbsent(key, id -> now.plusMillis(TOGGLE_DRAIN_PERIOD_MS));
            if (now.isBefore(next)) {
                continue;
            }
            nextToggleDrainAt.put(key, now.plusMillis(TOGGLE_DRAIN_PERIOD_MS));
            if (character.getResourceSystem().trySpendMana(toggle.toggleMpPerTick())) {
                if (character instanceof PlayerInstance player) {
                    player.send(new RegenTick(0, 0, player.getResourceSystem().getCurrentHealth(),
                            player.getResourceSystem().getMaxHealth(), player.getResourceSystem().getCurrentMana(),
                            player.getResourceSystem().getMaxMana()));
                }
                continue;
            }
            character.getEffectsSystem().remove(toggle.skillId());
            nextToggleDrainAt.remove(key);
            character.send(new ToggleDeactivated(toggle.skillName(), true));
            DomainEventPublisher.publish(new CharacterEffectExpired(character, toggle));
        }
    }

    // put et non putIfAbsent : un monstre réapparu est une NOUVELLE instance sous
    // le même UUID (MonsterRespawnEngine) — garder l'ancienne, morte, laisserait
    // les effets de la nouvelle sans expiration.
    public void register(AbstractCharacter character) {
        tracked.put(character.getId(), character);
        log.debug("effect.tracking_started character={}", character.getId());
    }

    // Comme dans L2, la mort fait tomber buffs et debuffs (poison compris) ; chaque
    // fin est publiée pour les clients, le groupe et la persistance.
    @EventListener
    void onCharacterDied(CharacterDied event) {
        for (ActiveEffect effect : event.character().getEffectsSystem().removeAllOnDeath()) {
            log.debug("effect.removed_on_death character={} effect={}", event.character().getId(), effect.skillName());
            DomainEventPublisher.publish(new CharacterEffectExpired(event.character(), effect));
        }
    }

    // Relax (breakOnAction) cesse au premier geste de son porteur : déplacement,
    // attaque ou nouvelle incantation.
    @EventListener
    void onCharacterStartedMoving(CharacterStartedMoving event) {
        breakOnAction(event.character());
    }

    @EventListener
    void onCharacterBeginAttack(CharacterBeginAttack event) {
        breakOnAction(event.attacker());
    }

    @EventListener
    void onSkillCastBegin(SkillCastBegin event) {
        breakOnAction(event.caster());
    }

    private void breakOnAction(AbstractCharacter character) {
        for (ActiveEffect effect : character.getEffectsSystem().removeBreakingOnAction()) {
            log.debug("effect.broken_by_action character={} effect={}", character.getId(), effect.skillName());
            DomainEventPublisher.publish(new CharacterEffectExpired(character, effect));
        }
    }

    // Le personnage quitte le jeu : plus aucun tick (poison, toggles, expiration)
    // sur cette instance déconnectée — un poison pourrait sinon la « tuer » hors
    // ligne et persister cette mort. PlayerLoadedInWorld réinscrit la nouvelle
    // instance à la reconnexion.
    @EventListener
    void onPlayerRemovedFromWorld(PlayerRemovedFromWorld event) {
        UUID id = event.character().getId();
        tracked.remove(id);
        nextToggleDrainAt.keySet().removeIf(key -> key.characterId().equals(id));
    }

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    void tick() {
        Instant now = Instant.now();
        for (AbstractCharacter character : tracked.values()) {
            try {
                applyPeriodicDamage(character, now);
                applyPeriodicHeal(character, now);
                drainToggles(character, now);
            } catch (Exception e) {
                log.error("effect.periodic_damage_failed character={}", character.getId(), e);
            }
            List<ActiveEffect> expired = character.getEffectsSystem().expireDue(now);
            for (ActiveEffect effect : expired) {
                DomainEventPublisher.publish(new CharacterEffectExpired(character, effect));
            }
            if (character.getEffectsSystem().isEmpty()) {
                tracked.remove(character.getId(), character);
                nextToggleDrainAt.keySet().removeIf(key -> key.characterId().equals(character.getId()));
                log.debug("effect.tracking_stopped character={}", character.getId());
            }
        }
    }
}
