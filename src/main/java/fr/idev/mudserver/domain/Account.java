package fr.idev.mudserver.domain;

import java.util.UUID;

public record Account(
        UUID id,
        String login,
        String password,
        UUID currentCharacterId
) {
}
