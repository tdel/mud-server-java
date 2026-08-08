package fr.idev.mudserver.telnet;

/**
 * Point unique de coloration ANSI des messages sortants : chaque catégorie
 * d'élément important en DnD5e (monstre, PNJ, dégâts, or, XP...) a sa propre
 * méthode plutôt que des codes ANSI dispersés dans chaque record de message,
 * pour que la palette reste modifiable en un seul endroit.
 */
public final class Ansi {

    private Ansi() {
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

    public static String item(String name) {
        return wrap(name, AnsiColor.BOLD, AnsiColor.GREEN);
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

    private static String wrap(String text, AnsiColor... colors) {
        StringBuilder prefix = new StringBuilder();
        for (AnsiColor color : colors) {
            prefix.append(color.code());
        }
        return prefix + text + AnsiColor.RESET.code();
    }
}
