package app.domain.actor.system;

import java.time.Duration;
import java.time.Instant;

import app.domain.actor.event.CharacterKarmaChanged;
import app.domain.actor.event.CharacterRecordedPlayerKill;
import app.domain.actor.event.CharacterRecordedPvpKill;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.PlayerPvpFlagCleared;
import app.domain.actor.event.PlayerPvpFlagged;
import app.domain.actor.instance.PlayerInstance;

public final class PvPSystem {

    // Comme en L2J : le flag PvP retombe après 10 minutes sans nouvelle attaque
    // sur un autre joueur (voir PvpEngine, qui parcourt les personnages en ligne
    // toutes les 5s pour comparer à pvpFlagExpiresAt).
    private static final Duration PVP_FLAG_DURATION = Duration.ofMinutes(10);

    public static final int KARMA_GAIN_PER_PK = 100;
    public static final int KARMA_LOSS_ON_DEATH = 100;
    public static final int KARMA_LOSS_PER_MONSTER_KILL = 1;

    private final PlayerInstance character;
    private int karma;
    private int pkCount;
    private int pvpCount;
    private volatile boolean pvpFlagged;
    private volatile Instant pvpFlagExpiresAt;

    public PvPSystem(PlayerInstance character, int karma, int pkCount, int pvpCount, boolean pvpFlagged) {
        this.character = character;
        this.karma = karma;
        this.pkCount = pkCount;
        this.pvpCount = pvpCount;
        this.pvpFlagged = pvpFlagged;
        this.pvpFlagExpiresAt = pvpFlagged ? Instant.now().plus(PVP_FLAG_DURATION) : null;
    }

    public int getKarma() {
        return karma;
    }

    public int getPkCount() {
        return pkCount;
    }

    public int getPvpCount() {
        return pvpCount;
    }

    public boolean isPvpFlagged() {
        return pvpFlagged;
    }

    // Pose ou prolonge le flag PvP ; ne republie l'événement qu'à la transition
    // non-flaggé -> flaggé, pour ne pas spammer un événement à chaque attaque
    // d'un joueur déjà flaggé (PvpEngine appelle cette méthode depuis
    // CharacterBeginAttack).
    public void flagPvp() {
        pvpFlagExpiresAt = Instant.now().plus(PVP_FLAG_DURATION);
        if (!pvpFlagged) {
            pvpFlagged = true;
            DomainEventPublisher.publish(new PlayerPvpFlagged(character));
        }
    }

    public boolean isPvpFlagExpired() {
        return pvpFlagged && Instant.now().isAfter(pvpFlagExpiresAt);
    }

    public void clearPvpFlag() {
        pvpFlagged = false;
        pvpFlagExpiresAt = null;
        DomainEventPublisher.publish(new PlayerPvpFlagCleared(character));
    }

    // Le karma ne descend jamais sous 0 (convention L2J : 0 = "innocent").
    public void addKarma(int amount) {
        int newKarma = Math.max(0, karma + amount);
        if (newKarma != karma) {
            karma = newKarma;
            DomainEventPublisher.publish(new CharacterKarmaChanged(character, karma));
        }
    }

    public void recordPlayerKill() {
        pkCount++;
        DomainEventPublisher.publish(new CharacterRecordedPlayerKill(character));
    }

    public void recordPvpKill() {
        pvpCount++;
        DomainEventPublisher.publish(new CharacterRecordedPvpKill(character));
    }
}
