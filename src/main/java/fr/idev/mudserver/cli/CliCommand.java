package fr.idev.mudserver.cli;

import org.springframework.boot.ApplicationArguments;

public interface CliCommand {

    /** Le premier argument positionnel qui déclenche cette commande (ex. "room-create"). */
    String name();

    int run(ApplicationArguments args);
}
