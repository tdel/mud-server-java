package app.network.message.ingame;

import java.util.List;

import app.network.OutputJsonMessage;

/**
 * Poussé par {@link app.domain.actor.KnownList} quand un portail entre dans la
 * portée de perception.
 */
public record PortalAppeared(List<PortalView> portals) implements OutputJsonMessage {
}
