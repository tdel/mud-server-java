package fr.idev.mudserver.network.server.telnet;

public final class TelnetEcho {

    public static final byte[] OFF = {(byte) 0xFF, (byte) 0xFB, 0x01}; // IAC WILL ECHO
    public static final byte[] ON = {(byte) 0xFF, (byte) 0xFC, 0x01}; // IAC WONT ECHO

    private TelnetEcho() {
    }
}
