package app.network.message.ingame;

import app.network.OutputJsonMessage;

/**
 * Commande refusée : le personnage est en train d'être téléporté (fin d'un
 * Scroll of Escape, cf. EscapeEngine).
 */
public record TeleportInProgress() implements OutputJsonMessage {

}
