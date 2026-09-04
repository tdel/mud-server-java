package app.network.command.ingame;

import java.util.Locale;
import java.util.Optional;

import app.domain.item.ItemGrade;

// Partagé par Soulshot/Spiritshot : "off" désactive explicitement l'auto-use, un nom de
// grade (nograde/none/d/c/b/a/s) l'active. Optional.empty() != "off" — voir les deux
// call sites pour la distinction entre "argument invalide" et "désactivation demandée".
final class ShotGradeArgument {

    private ShotGradeArgument() {
    }

    static final String OFF = "off";

    static Optional<ItemGrade> parse(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "nograde", "none" -> Optional.of(ItemGrade.NOGRADE);
            case "d" -> Optional.of(ItemGrade.D);
            case "c" -> Optional.of(ItemGrade.C);
            case "b" -> Optional.of(ItemGrade.B);
            case "a" -> Optional.of(ItemGrade.A);
            case "s" -> Optional.of(ItemGrade.S);
            default -> Optional.empty();
        };
    }
}
