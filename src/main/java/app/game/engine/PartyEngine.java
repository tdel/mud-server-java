package app.game.engine;

import java.util.Collection;

import app.game.WorldInstanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import app.domain.PendingPartyInvite;
import app.domain.actor.event.PlayerRemovedFromWorld;
import app.domain.actor.instance.CharacterInstance;
import app.network.message.ingame.PartyInviteDeclined;

@Service
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

    void expireInvites(Collection<CharacterInstance> onlineCharacters) {
        long now = System.currentTimeMillis();
        for (CharacterInstance character : onlineCharacters) {
            PendingPartyInvite invite = character.getPendingInvite();
            if (invite != null && now - invite.sentAtMillis() >= INVITE_TIMEOUT_MS) {
                character.setPendingInvite(null);
                character.send(new PartyInviteDeclined(invite.inviter().getName()));
                invite.inviter().send(new PartyInviteDeclined(character.getName()));
                log.info("party.invite_expired inviter={} target={}", invite.inviter().getId(), character.getId());
            }
        }
    }

    @EventListener
    void onPlayerRemovedFromWorld(PlayerRemovedFromWorld event) {
        CharacterInstance character = event.character();
        character.setPendingInvite(null);
        if (character.getParty() != null) {
            character.getParty().removeAndNotify(character);
        }
    }
}
