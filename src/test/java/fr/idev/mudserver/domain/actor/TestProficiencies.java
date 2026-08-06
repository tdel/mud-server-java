package fr.idev.mudserver.domain.actor;

import java.util.Set;

import fr.idev.mudserver.game.actor.ClassService;
import tools.jackson.databind.ObjectMapper;

/**
 * Sur le même principe que {@link TestAttributes} : évite d'injecter
 * {@link ClassService} dans chaque test qui construit un {@code GamePlayer} à
 * la main. Instance {@code ClassService} dédiée aux tests, jamais celle gérée
 * par Spring — un {@code ClassService} n'a pas d'état mutable en jeu une fois
 * réchauffé, donc la partager ainsi entre tests ne pose aucun problème
 * d'isolation.
 */
public final class TestProficiencies {

    private static final ClassService CLASS_SERVICE = new ClassService(new ObjectMapper());

    static {
        CLASS_SERVICE.warmClassDefinitions();
    }

    private TestProficiencies() {
    }

    public static Set<Attribute> savingThrows(CharacterClass characterClass) {
        return CLASS_SERVICE.savingThrowProficiencies(characterClass);
    }

    public static Set<Skill> skills(CharacterClass characterClass) {
        return CLASS_SERVICE.skillProficiencies(characterClass);
    }
}
