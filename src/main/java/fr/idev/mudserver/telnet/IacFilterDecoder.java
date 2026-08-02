package fr.idev.mudserver.telnet;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

/**
 * Retire les séquences IAC (subnégociation, négociation d'option) du flux
 * entrant, sans jamais répondre à une négociation côté serveur — seul le toggle
 * ECHO explicite de {@link TelnetConnection#promptMasked} émet de l'IAC, en
 * sortie. Port du filtre par regex PHP (IacFilter::strip()) en scan d'octets,
 * nécessaire ici car un decoder Netty ne peut pas travailler sur un buffer déjà
 * entièrement reçu : une séquence IAC peut être coupée entre deux paquets TCP.
 */
public class IacFilterDecoder extends ByteToMessageDecoder {

    private static final int IAC = 0xFF;
    private static final int SB = 250;
    private static final int SE = 240;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        ByteBuf cleaned = ctx.alloc().buffer(in.readableBytes());
        boolean wroteAny = false;
        while (in.isReadable()) {
            in.markReaderIndex();
            int b = in.readUnsignedByte();
            if (b != IAC) {
                cleaned.writeByte(b);
                wroteAny = true;
                continue;
            }
            if (!in.isReadable()) {
                in.resetReaderIndex();
                break;
            }
            int cmd = in.readUnsignedByte();
            if (cmd == IAC) {
                cleaned.writeByte(IAC);
                wroteAny = true;
                continue;
            }
            if (cmd == SB) {
                if (!skipUntilSe(in)) {
                    in.resetReaderIndex();
                    break;
                }
                continue;
            }
            if (cmd >= 251 && cmd <= 254) { // WILL/WONT/DO/DONT
                if (!in.isReadable()) {
                    in.resetReaderIndex();
                    break;
                }
                in.readUnsignedByte(); // option, ignorée : pas de négociation côté serveur
            }
            // sinon : autre commande IAC à 2 octets (NOP, AYT, ...), déjà consommée,
            // ignorée
        }
        if (wroteAny) {
            out.add(cleaned);
        } else {
            cleaned.release();
        }
    }

    private boolean skipUntilSe(ByteBuf in) {
        while (in.isReadable()) {
            int b = in.readUnsignedByte();
            if (b == IAC && in.isReadable() && in.getUnsignedByte(in.readerIndex()) == SE) {
                in.readUnsignedByte();
                return true;
            }
        }
        return false;
    }
}
