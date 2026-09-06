package app.network.message.ingame;

import java.util.List;
import java.util.UUID;

import app.network.OutputJsonMessage;

/**
 * Poussé par {@link app.domain.actor.KnownList} quand un portail sort de la
 * portée de perception.
 */
public record PortalDisappeared(List<UUID> portalIds) implements OutputJsonMessage {
}
