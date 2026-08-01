package fr.idev.mudserver.cli;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Chaque commande CLI (room-create, item-template-create, item-template-load, item-spawn) est
 * un process JVM court invoqué en argument positionnel (ex. {@code java -jar app.jar
 * room-create --name=Foo --description=Bar}), partageant les mêmes DAOs que le serveur telnet
 * sans jamais le démarrer : {@code ApplicationRunner} s'exécute avant la publication
 * d'{@code ApplicationReadyEvent} (qui déclenche {@code TelnetServer.start()}), et
 * {@link System#exit} ici court-circuite le reste du démarrage de {@code SpringApplication}
 * avant que ça n'arrive. Sans argument positionnel reconnu (démarrage normal du serveur), cette
 * méthode ne fait rien et laisse le boot continuer normalement.
 */
@Component
public class CliCommandRunner implements ApplicationRunner {

    private final Map<String, CliCommand> commandsByName;

    public CliCommandRunner(List<CliCommand> commands) {
        this.commandsByName = commands.stream().collect(Collectors.toMap(CliCommand::name, Function.identity()));
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> positional = args.getNonOptionArgs();
        if (positional.isEmpty()) {
            return;
        }

        String commandName = positional.get(0);
        CliCommand command = commandsByName.get(commandName);

        int exitCode;
        if (command == null) {
            System.err.println("Unknown command: " + commandName + ". Known commands: " + commandsByName.keySet());
            exitCode = 1;
        } else {
            exitCode = command.run(args);
        }

        System.exit(exitCode);
    }
}
