package fr.idev.mudserver.network.server.telnet;

enum AnsiColor {

    RESET("\u001B[0m"), BOLD("\u001B[1m"), RED("\u001B[31m"), GREEN("\u001B[32m"), YELLOW("\u001B[33m"), BLUE(
            "\u001B[34m"), MAGENTA("\u001B[35m"), CYAN("\u001B[36m"), WHITE("\u001B[37m");

    private final String code;

    AnsiColor(String code) {
        this.code = code;
    }

    String code() {
        return code;
    }
}
