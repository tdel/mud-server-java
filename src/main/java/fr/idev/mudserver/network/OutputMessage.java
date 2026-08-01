package fr.idev.mudserver.network;

/**
 * Marqueur pour tout ce qui peut être envoyé à une session — volontairement agnostique du
 * transport. Le rendu pour un transport donné (telnet, un futur HTTP/websocket...) est
 * défini par une sous-interface spécifique (voir {@code telnet.OutputTelnetMessage}).
 */
public interface OutputMessage {
}
