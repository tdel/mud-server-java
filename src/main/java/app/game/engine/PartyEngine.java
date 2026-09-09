package app.game.engine;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;

import app.game.WorldInstanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import app.domain.Party;
import app.domain.PendingPartyInvite;
import app.domain.SkillEffectType;
import app.domain.actor.event.CharacterDamaged;
import app.domain.actor.event.CharacterLeveledUp;
import app.domain.actor.event.CharacterRegenerated;
import app.domain.actor.event.GamePlayerRespawned;
import app.domain.actor.event.GamePlayerUsedManaPotion;
import app.domain.actor.event.GamePlayerUsedPotion;
import app.domain.actor.event.PlayerRemovedFromWorld;
import app.domain.actor.event.SkillCast;
import app.domain.actor.instance.PlayerInstance;
import app.network.message.ingame.PartyInviteDeclined;
import app.network.message.ingame.PartyMemberEffectApplied;
import app.network.message.ingame.PartyMemberVitalsUpdated;

@Component
public class PartyEngine {

    private static final Logger log = LoggerFactory.getLogger(PartyEngine.class);

    static final long INVITE_TIMEOUT_MS = 20_000L;
    private static final long TICK_INTERVAL_MS = 1_000L;

    private final WorldInstanceService worldInstanceService;

    public PartyEngine(WorldInstanceService worldInstanceService) {
        this.worldInstanceService = worldInstanceService;
    }

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    void tick() {
        if (!worldInstanceService.isDefaultWorldMaterialized()) {
            return;
        }
        expireInvites(worldInstanceService.getDefaultInstance().onlineCharacters());
    }

    void expireInvites(Collection<PlayerInstance> onlineCharacters) {
        long now = System.currentTimeMillis();
        for (PlayerInstance character : onlineCharacters) {
            PendingPartyInvite invite = character.getPartySystem().getPendingInvite();
            if (invite != null && now - invite.sentAtMillis() >= INVITE_TIMEOUT_MS) {
                character.getPartySystem().setPendingInvite(null);
                character.send(new PartyInviteDeclined(invite.inviter().getName()));
                invite.inviter().send(new PartyInviteDeclined(character.getName()));
                log.info("party.invite_expired inviter={} target={}", invite.inviter().getId(), character.getId());
            }
        }
    }

    @EventListener
    void onPlayerRemovedFromWorld(PlayerRemovedFromWorld event) {
        PlayerInstance character = event.character();
        character.getPartySystem().setPendingInvite(null);
        if (character.getPartySystem().getParty() != null) {
            character.getPartySystem().getParty().removeAndNotify(character);
        }
    }

    @EventListener
    void onCharacterDamaged(CharacterDamaged event) {
        if (event.character() instanceof PlayerInstance character) {
            broadcastVitals(character);
        }
    }

    @EventListener
    void onCharacterLeveledUp(CharacterLeveledUp event) {
        broadcastVitals(event.character());
    }

    @EventListener
    void onGamePlayerRespawned(GamePlayerRespawned event) {
        broadcastVitals(event.character());
    }

    @EventListener
    void onGamePlayerUsedPotion(GamePlayerUsedPotion event) {
        broadcastVitals(event.character());
    }

    @EventListener
    void onGamePlayerUsedManaPotion(GamePlayerUsedManaPotion event) {
        broadcastVitals(event.character());
    }

    @EventListener
    void onCharacterRegenerated(CharacterRegenerated event) {
        broadcastVitals(event.character());
    }

    // Filtre le miroir de ActiveEffectPersistenceListener.onSkillCast (cible
    // joueur ET buff/debuff appliqué) : les deux listeners partagent SkillCast
    // sans dépendance d'ordre entre eux (cf. CLAUDE.md).
    @EventListener
    void onSkillCast(SkillCast event) {
        boolean modifier = event.activeSkill().skillType() == SkillEffectType.BUFF
                || event.activeSkill().skillType() == SkillEffectType.DEBUFF;
        if (!event.hit() || !modifier || !(event.target() instanceof PlayerInstance targetPlayer)) {
            return;
        }
        Party party = targetPlayer.getPartySystem().getParty();
        if (party != null) {
            long secondsRemaining = Duration.between(Instant.now(), event.expiresAt()).toSeconds();
            String statLabel = event.modifiers().isEmpty() ? "" : event.modifiers().get(0).stat().label();
            party.broadcast(
                    new PartyMemberEffectApplied(targetPlayer.getId(), targetPlayer.getName(),
                            event.activeSkill().name(), statLabel, event.amount(), Math.max(0, secondsRemaining)),
                    targetPlayer);
        }
    }

    private void broadcastVitals(PlayerInstance character) {
        Party party = character.getPartySystem().getParty();
        if (party != null) {
            party.broadcast(new PartyMemberVitalsUpdated(character.getId(), character.getName(),
                    character.getResourceSystem().getCurrentHealth(), character.getResourceSystem().getMaxHealth(),
                    character.getResourceSystem().getCurrentMana(), character.getResourceSystem().getMaxMana()),
                    character);
        }
    }
}
