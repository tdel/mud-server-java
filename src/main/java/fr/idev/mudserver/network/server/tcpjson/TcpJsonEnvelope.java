package fr.idev.mudserver.network.server.tcpjson;

public record TcpJsonEnvelope(String type, Object payload, boolean secure) {
}
