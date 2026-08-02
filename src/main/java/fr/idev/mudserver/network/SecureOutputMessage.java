package fr.idev.mudserver.network;

/**
 * Marqueur pour un {@link OutputMessage} dont la réponse attendue doit être
 * masquée côté client (mot de passe...). Le transport décide comment s'y
 * prendre concrètement (ex. toggle d'echo telnet dans
 * {@code TelnetConnection#requestBlocking}).
 */
public interface SecureOutputMessage extends OutputMessage {
}
