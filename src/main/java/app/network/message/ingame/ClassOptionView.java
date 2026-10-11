package app.network.message.ingame;

import java.util.List;

import app.domain.actor.Subclass;

// Profession proposée au changement de classe : `name` est l'identifiant à
// renvoyer dans choose-subclass, `label` son nom L2.
public record ClassOptionView(Subclass name, String label, String description) {

    public static ClassOptionView of(Subclass subclass) {
        return new ClassOptionView(subclass, subclass.label(), subclass.description());
    }

    public static List<ClassOptionView> listOf(List<Subclass> subclasses) {
        return subclasses.stream().map(ClassOptionView::of).toList();
    }
}
