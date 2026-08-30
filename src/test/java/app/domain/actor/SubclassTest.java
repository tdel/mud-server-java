package app.domain.actor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubclassTest {

    @Test
    void tier1OffersOptionsPerBaseClass() {
        assertThat(Subclass.availableAt(CharacterClass.FIGHTER, 1)).containsExactly(Subclass.WARRIOR, Subclass.KNIGHT,
                Subclass.ROGUE);
        assertThat(Subclass.availableAt(CharacterClass.MYSTIC, 1)).containsExactly(Subclass.WIZARD, Subclass.CLERIC);
    }

    @Test
    void tier2HasNoOptionsYet() {
        assertThat(Subclass.availableAt(CharacterClass.FIGHTER, 2)).isEmpty();
        assertThat(Subclass.availableAt(CharacterClass.MYSTIC, 2)).isEmpty();
    }
}
