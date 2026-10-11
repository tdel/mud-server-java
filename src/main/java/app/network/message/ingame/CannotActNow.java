package app.network.message.ingame;

import app.network.OutputJsonMessage;

// Action refusée : personnage étourdi, endormi, paralysé, terrorisé, enraciné
// ou en mort feinte (reason affichable).
public record CannotActNow(String reason) implements OutputJsonMessage {
}
