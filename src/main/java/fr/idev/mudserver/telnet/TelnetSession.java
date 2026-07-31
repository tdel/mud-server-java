package fr.idev.mudserver.telnet;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;

/**
 * État par connexion, porté par un attribut de {@link Channel}. Netty garantit qu'un seul
 * virtual thread touche une connexion donnée à la fois (ordre de lecture préservé par
 * channel, un seul {@code channelRead0} traité avant le suivant) — aucune synchronisation
 * supplémentaire n'est donc nécessaire sur ces champs mutables.
 */
public class TelnetSession {

    private final Channel channel;
    private Consumer<String> lineHandler = line -> { };
    private Consumer<String> pendingLine;

    public TelnetSession(Channel channel) {
        this.channel = channel;
    }

    public void setLineHandler(Consumer<String> lineHandler) {
        this.lineHandler = lineHandler;
    }

    public void handleLine(String line) {
        Consumer<String> handler = pendingLine;
        if (handler != null) {
            pendingLine = null;
            handler.accept(line);
        } else {
            lineHandler.accept(line);
        }
    }

    public void awaitLine(Consumer<String> handler) {
        this.pendingLine = handler;
    }

    /** Coupe l'echo local du client, lit une ligne, puis rétablit l'echo — pour un mot de passe. */
    public void promptMasked(String prompt, Consumer<String> onLine) {
        writeRaw(TelnetEcho.OFF);
        write(prompt);
        awaitLine(line -> {
            writeRaw(TelnetEcho.ON);
            write("\r\n");
            onLine.accept(line);
        });
    }

    public void write(String text) {
        channel.writeAndFlush(Unpooled.copiedBuffer(text, StandardCharsets.UTF_8));
    }

    private void writeRaw(byte[] bytes) {
        channel.writeAndFlush(Unpooled.wrappedBuffer(bytes));
    }

    public void close() {
        channel.close();
    }
}
