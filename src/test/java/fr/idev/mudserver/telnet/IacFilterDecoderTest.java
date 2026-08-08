package fr.idev.mudserver.telnet;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link IacFilterDecoder} n'a aucune dépendance Spring/DB : couvert via
 * {@link EmbeddedChannel}, le double de test officiel de Netty pour un
 * {@code ByteToMessageDecoder}, plutôt qu'un mock.
 */
class IacFilterDecoderTest {

    private static final int IAC = 0xFF;
    private static final int WILL = 251;
    private static final int WONT = 252;
    private static final int DO = 253;
    private static final int DONT = 254;
    private static final int SB = 250;
    private static final int SE = 240;

    private final EmbeddedChannel channel = new EmbeddedChannel(new IacFilterDecoder());

    @Test
    void willDoDontWontSequencesAreStrippedFromOutput() {
        writeInbound("before", IAC, WILL, 1, IAC, WONT, 1, IAC, DO, 24, IAC, DONT, 24);
        writeInbound("after");

        assertThat(readAllDecoded()).isEqualTo("beforeafter");
    }

    @Test
    void subnegotiationBlockIsSkippedEntirely() {
        writeInbound("before", IAC, SB, 24, 0, 'x', 'y', 'z', IAC, SE, "after");

        assertThat(readAllDecoded()).isEqualTo("beforeafter");
    }

    @Test
    void escapedIacIacBecomesLiteral0xFF() {
        writeInbound(IAC, IAC);

        assertThat(readAllDecoded()).isEqualTo("ÿ");
    }

    @Test
    void plainTextWithNoIacPassesThroughUnchanged() {
        writeInbound("hello world");

        assertThat(readAllDecoded()).isEqualTo("hello world");
    }

    @Test
    void iacSequenceSplitAcrossTwoWritesIsReassembledCorrectly() {
        boolean firstWriteProducedOutput = channel.writeInbound(bytes((Object) new int[]{IAC}));
        assertThat(firstWriteProducedOutput).isFalse();
        assertThat((ByteBuf) channel.readInbound()).isNull();

        writeInbound(WILL, 1, "after");

        assertThat(readAllDecoded()).isEqualTo("after");
    }

    @Test
    void subnegotiationSplitAcrossTwoWritesIsHandledCorrectly() {
        boolean firstWriteProducedOutput = channel.writeInbound(bytes("before", IAC, SB, 24, 0, 'x'));
        assertThat(firstWriteProducedOutput).isTrue();
        assertThat(readAllDecoded()).isEqualTo("before");

        writeInbound('y', 'z', IAC, SE, "after");

        assertThat(readAllDecoded()).isEqualTo("after");
    }

    private void writeInbound(Object... parts) {
        channel.writeInbound(bytes(parts));
    }

    private ByteBuf bytes(Object... parts) {
        ByteBuf buf = Unpooled.buffer();
        for (Object part : parts) {
            if (part instanceof String s) {
                buf.writeBytes(s.getBytes(StandardCharsets.ISO_8859_1));
            } else if (part instanceof Character c) {
                buf.writeByte(c);
            } else if (part instanceof int[] arr) {
                for (int b : arr) {
                    buf.writeByte(b);
                }
            } else if (part instanceof Integer i) {
                buf.writeByte(i);
            } else {
                throw new IllegalArgumentException("Unsupported part: " + part);
            }
        }
        return buf;
    }

    private String readAllDecoded() {
        StringBuilder text = new StringBuilder();
        ByteBuf decoded;
        while ((decoded = channel.readInbound()) != null) {
            text.append(decoded.toString(StandardCharsets.ISO_8859_1));
            decoded.release();
        }
        return text.toString();
    }
}
