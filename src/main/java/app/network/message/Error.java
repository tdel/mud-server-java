package app.network.message;

import app.network.OutputJsonMessage;

// Message d'erreur générique, affiché tel quel par le client.
public record Error(String message) implements OutputJsonMessage {
}
