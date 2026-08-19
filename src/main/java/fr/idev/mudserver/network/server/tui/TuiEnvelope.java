package fr.idev.mudserver.network.server.tui;

public record TuiEnvelope(String type, Object payload, boolean secure) {
}
