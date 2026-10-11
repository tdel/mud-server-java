package app.network.message;

import app.network.OutputJsonMessage;

// Heartbeat : envoyé par TcpJsonSessionHandler quand le client se tait trop
// longtemps ; le client doit répondre par la commande "pong" (voir Pong).
public record Ping() implements OutputJsonMessage {

}
