package fr.idev.mudserver.cli;

import java.util.List;

import org.springframework.boot.ApplicationArguments;

final class CliOptions {

    private CliOptions() {
    }

    static String first(ApplicationArguments args, String optionName) {
        List<String> values = args.getOptionValues(optionName);
        return values == null || values.isEmpty() ? null : values.get(0);
    }
}
