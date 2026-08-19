package fr.idev.mudserver.network.server.tui;

public interface JsonOutput {
    void write(String type, Object payload, boolean secure);
}
