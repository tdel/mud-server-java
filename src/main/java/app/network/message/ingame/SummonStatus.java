package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

// État du serviteur invoqué, envoyé à son maître (apparition, dégâts reçus) :
// fenêtre de serviteur du client. model : modèle 3D (cat, panther, undead) ;
// secondsRemaining : temps avant son départ.
public record SummonStatus(UUID summonId, String name, String model, int level, int currentHealth, int maxHealth,
        long secondsRemaining) implements OutputJsonMessage {
}
