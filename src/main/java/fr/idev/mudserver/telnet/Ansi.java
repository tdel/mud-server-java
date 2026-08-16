package fr.idev.mudserver.telnet;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fr.idev.mudserver.domain.item.Rarity;

public final class Ansi {

    private static final Pattern GRID_LEGEND_TOKEN = Pattern.compile("([@pmn#]) = ");

    private Ansi() {
    }

    public static String self(String text) {
        return wrap(text, AnsiColor.BOLD, AnsiColor.WHITE);
    }

    public static String player(String name) {
        return wrap(name, AnsiColor.BOLD, AnsiColor.CYAN);
    }

    public static String monster(String name) {
        return wrap(name, AnsiColor.BOLD, AnsiColor.RED);
    }

    public static String npc(String name) {
        return wrap(name, AnsiColor.BOLD, AnsiColor.YELLOW);
    }

    public static String item(String name, Rarity rarity) {
        return switch (rarity) {
            case COMMON -> wrap(name, AnsiColor.WHITE);
            case UNCOMMON -> wrap(name, AnsiColor.BOLD, AnsiColor.GREEN);
            case RARE -> wrap(name, AnsiColor.BOLD, AnsiColor.BLUE);
            case VERY_RARE -> wrap(name, AnsiColor.BOLD, AnsiColor.MAGENTA);
            case LEGENDARY -> wrap(name, AnsiColor.BOLD, AnsiColor.YELLOW);
            case ARTIFACT -> wrap(name, AnsiColor.BOLD, AnsiColor.RED);
        };
    }

    public static String room(String name) {
        return wrap(name, AnsiColor.BOLD, AnsiColor.BLUE);
    }

    public static String damage(int amount) {
        return wrap(String.valueOf(amount), AnsiColor.RED);
    }

    public static String heal(int amount) {
        return wrap(String.valueOf(amount), AnsiColor.GREEN);
    }

    public static String gold(int amount) {
        return wrap(String.valueOf(amount), AnsiColor.YELLOW);
    }

    public static String xp(int amount) {
        return wrap(String.valueOf(amount), AnsiColor.MAGENTA);
    }

    public static String critical(String text) {
        return wrap(text, AnsiColor.BOLD, AnsiColor.RED);
    }

    public static String levelUp(String text) {
        return wrap(text, AnsiColor.BOLD, AnsiColor.MAGENTA);
    }

    public static String error(String text) {
        return wrap(text, AnsiColor.RED);
    }

    public static String success(String text) {
        return wrap(text, AnsiColor.GREEN);
    }

    public static String dice(String text) {
        return wrap(text, AnsiColor.CYAN);
    }

    public static String gridLine(String line) {
        StringBuilder colored = new StringBuilder(line.length() * 8);
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            colored.append(switch (c) {
                case '@' -> self(String.valueOf(c));
                case 'p' -> player(String.valueOf(c));
                case 'm' -> monster(String.valueOf(c));
                case 'n' -> npc(String.valueOf(c));
                case '#' -> room(String.valueOf(c));
                case '*' -> room(String.valueOf('X')); // case d'arrivée sur un portail : même couleur qu'un portail
                default -> String.valueOf(c);
            });
        }
        return colored.toString();
    }

    public static String gridLegend(String plainLegend) {
        Matcher matcher = GRID_LEGEND_TOKEN.matcher(plainLegend);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String glyph = matcher.group(1);
            String colored = switch (glyph) {
                case "@" -> self(glyph);
                case "p" -> player(glyph);
                case "m" -> monster(glyph);
                case "n" -> npc(glyph);
                case "#" -> room(glyph);
                default -> glyph;
            };
            matcher.appendReplacement(result, Matcher.quoteReplacement(colored + " = "));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String wrap(String text, AnsiColor... colors) {
        StringBuilder prefix = new StringBuilder();
        for (AnsiColor color : colors) {
            prefix.append(color.code());
        }
        return prefix + text + AnsiColor.RESET.code();
    }
}
