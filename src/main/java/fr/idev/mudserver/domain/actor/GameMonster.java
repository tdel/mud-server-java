package fr.idev.mudserver.domain.actor;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import fr.idev.mudserver.domain.actor.event.CharacterDied;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;

/**
 * Contrairement à {@link Item}, qui délègue {@code getName()}/{@code getType()}
 * à son template en lecture à chaque appel, {@code GameMonster} copie au moment
 * de sa construction (voir {@code MonsterService.warmMonsters}) le nom, les
 * attributs et les PV max du {@link MonsterTemplate} résolu dans les champs
 * hérités de {@link GameCharacter} — ces champs sont la représentation partagée
 * unique utilisée par {@link GamePlayer}/{@link GameMonster}/ {@link GameNpc}
 * ({@code getModifier}/{@code getCurrentHealth} doivent fonctionner pareil pour
 * les trois, pas de délégation différente par sous-type). Le template reste
 * néanmoins attaché pour exposer {@link #getDescription()} par délégation, un
 * champ propre au flavor text qui n'a pas sa place sur {@code GameCharacter}.
 */
public final class GameMonster extends GameCharacter {

    private final UUID templateId;
    private final UUID roomId;

    private MonsterTemplate template;

    public GameMonster(UUID id, String name, UUID templateId, UUID roomId, Map<Attribute, Integer> attributes,
            int maxHealth) {
        super(id, name, attributes, maxHealth, maxHealth);
        this.templateId = templateId;
        this.roomId = roomId;
    }

    /**
     * Verrou sur l'instance vivante unique (monstres jamais rechargés, warmés une
     * seule fois par {@code MonsterService}) — même pattern que
     * {@code GamePlayer#pickUpItem}. Deux joueurs attaquant ce monstre en même
     * temps se sérialisent ici ; celui dont l'appel fait passer les PV à 0 ou moins
     * reçoit {@code true} (« coup fatal »), un seul gagnant possible : la garde sur
     * un monstre déjà à 0 PV empêche un appel concurrent arrivé juste après de se
     * croire lui aussi le coup fatal. {@code synchronized} ne pine plus les virtual
     * threads sur leur carrier depuis JEP 491 (JDK 24+).
     *
     * <p>
     * Sur le coup fatal, publie {@link CharacterDied} — hors du bloc
     * {@code synchronized}, même principe que {@code GamePlayer#pickUpItem}
     * (trancher sous verrou, publier après) : c'est cette méthode, plutôt que
     * l'appelant, qui garantit qu'un événement part dès que ce monstre meurt, quel
     * que soit le code qui a déclenché le coup ({@code CombatService.tryAttack} ne
     * fait qu'exposer les dégâts à appliquer, voir sa Javadoc).
     *
     * @return true si ce coup est celui qui a fait passer les PV à 0 ou moins
     */
    public boolean takeDamage(int amount, GamePlayer attacker) {
        boolean defeated;
        synchronized (this) {
            if (getCurrentHealth() <= 0) {
                return false;
            }
            setCurrentHealth(Math.max(0, getCurrentHealth() - amount));
            defeated = getCurrentHealth() <= 0;
        }
        if (defeated) {
            DomainEventPublisher.publish(new CharacterDied(this, attacker));
        }
        return defeated;
    }

    public void attachTemplate(MonsterTemplate template) {
        this.template = template;
    }

    public MonsterTemplate getTemplate() {
        return template;
    }

    public String getDescription() {
        return requireTemplate().getDescription();
    }

    public String getNaturalDamageDice() {
        return requireTemplate().getNaturalDamageDice();
    }

    public int getPresenceRadius() {
        return requireTemplate().getPresenceRadius();
    }

    @Override
    public int getArmorClass() {
        Integer natural = requireTemplate().getNaturalArmorClass();
        return natural != null ? natural : super.getArmorClass();
    }

    private MonsterTemplate requireTemplate() {
        if (template == null) {
            throw new IllegalStateException("GameMonster " + getId() + " has no MonsterTemplate attached");
        }
        return template;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public UUID getRoomId() {
        return roomId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GameMonster other)) {
            return false;
        }
        return getCurrentHealth() == other.getCurrentHealth() && getMaxHealth() == other.getMaxHealth()
                && Objects.equals(getId(), other.getId()) && Objects.equals(getName(), other.getName())
                && Objects.equals(templateId, other.templateId) && Objects.equals(roomId, other.roomId)
                && Objects.equals(getAttributes(), other.getAttributes());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getName(), templateId, roomId, getAttributes(), getCurrentHealth(),
                getMaxHealth());
    }

    @Override
    public String toString() {
        return "GameMonster[id=" + getId() + ", name=" + getName() + ", templateId=" + templateId + ", roomId=" + roomId
                + ", currentHealth=" + getCurrentHealth() + ", maxHealth=" + getMaxHealth() + "]";
    }
}
