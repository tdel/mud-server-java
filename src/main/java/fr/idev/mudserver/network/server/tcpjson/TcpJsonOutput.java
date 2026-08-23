package fr.idev.mudserver.network.server.tcpjson;

public interface TcpJsonOutput {
    void write(String type, Object payload, boolean secure);
}
