package fr.idev.mudserver.telnet;

/** Octets IAC bruts pour couper/rétablir l'echo local du client (prompt de mot de passe). */
public final class TelnetEcho {

    public static final byte[] OFF = {(byte) 0xFF, (byte) 0xFB, 0x01}; // IAC WILL ECHO
    public static final byte[] ON = {(byte) 0xFF, (byte) 0xFC, 0x01};  // IAC WONT ECHO

    private TelnetEcho() {
    }
}
