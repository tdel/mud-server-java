package app.network;

import java.util.Optional;
import java.util.UUID;

public final class CommandArguments {

    private CommandArguments() {
    }

    public static Optional<UUID> tryParseUuid(String token) {
        try {
            return Optional.of(UUID.fromString(token));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
