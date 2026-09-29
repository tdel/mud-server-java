package app.network.message.ingame;

import java.util.List;
import java.util.UUID;

import app.domain.actor.AbstractCharacter;
import app.domain.actor.AbstractNpc;
import app.domain.actor.Gender;
import app.domain.actor.NpcType;
import app.domain.actor.instance.PlayerInstance;
import app.domain.actor.instance.MonsterInstance;
import app.domain.actor.instance.NpcSellerInstance;
import app.domain.map.Position;
import app.game.engine.MovementEngine;

public record EntityView(UUID id, String name, String title, String kind, double x, double y, double heading,
        double speed, int currentHealth, int maxHealth, int level, Double targetX, Double targetY, boolean hasShop,
        boolean pvpFlagged, Gender gender, List<EquipmentView> equipment, List<Position> waypoints, NpcType npcType) {

    public static EntityView of(AbstractCharacter character) {
        MovementEngine.ActiveMovement movement = character.getMotionSystem().getActiveMovement();
        Double targetX = null;
        Double targetY = null;
        // Chemin restant (destination incluse) : le client suit la même
        // trajectoire que le serveur pour une entité déjà en mouvement quand elle
        // entre en perception.
        List<Position> waypoints = List.of();
        if (movement != null && !movement.remainingWaypoints().isEmpty()) {
            waypoints = movement.remainingWaypoints();
            Position destination = waypoints.get(waypoints.size() - 1);
            targetX = destination.x();
            targetY = destination.y();
        }
        // "character"/"npc"/"monster" (pas "player") pour matcher tel quel les préfixes
        // de clé
        // déjà utilisés côté client 3D (Game3D.gd,
        // "character:<nom>"/"npc:<nom>"/"monster:<nom>",
        // repris de l'ancien MapEnter à 3 listes) : aucune table de traduction
        // nécessaire.
        // Sexe + équipement porté : pour un joueur, pour que le client affiche le bon
        // modèle habillé — tenu à jour ensuite par CharacterAppearanceChanged. Un PNJ
        // n'envoie que son sexe et son rôle (npcType) : le client en déduit sa tenue
        // (GUARD : armure lourde, bouclier, épée courte). Null pour un monstre.
        Gender gender = character instanceof PlayerInstance player
                ? player.getAppearanceSystem().getGender()
                : character instanceof AbstractNpc npc ? npc.getGender() : null;
        NpcType npcType = character instanceof AbstractNpc npc ? npc.getNpcType() : null;
        List<EquipmentView> equipment = character instanceof PlayerInstance player
                ? EquipmentView.listOf(player)
                : List.of();
        String kind = character instanceof MonsterInstance
                ? "monster"
                : character instanceof AbstractNpc ? "npc" : "character";
        return new EntityView(character.getId(), character.getName(), character.getTitle(), kind,
                character.getMotionSystem().getPosition().x(), character.getMotionSystem().getPosition().y(),
                character.getMotionSystem().getHeading(),
                MovementEngine.unitsPerSecond(character.getMotionSystem().getSpeed()),
                character.getResourceSystem().getCurrentHealth(), character.getResourceSystem().getMaxHealth(),
                character.getLevelingSystem().getLevel(), targetX, targetY, character instanceof NpcSellerInstance,
                character instanceof PlayerInstance player && player.getPvpSystem().isPvpFlagged(), gender, equipment,
                waypoints, npcType);
    }
}
