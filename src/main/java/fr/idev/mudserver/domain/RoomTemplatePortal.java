package fr.idev.mudserver.domain;

import java.util.UUID;

/**
 * Version statique de {@link RoomPortal}, portée par un {@link RoomTemplate} :
 * la cible est un id de {@code RoomTemplate}, pas une référence d'objet,
 * puisque le même portail doit se résoudre vers une {@code RoomInstance} sœur
 * différente dans chaque instance de World qui matérialise ce template (voir
 * {@code WorldInstanceService}, à venir). Résolu en objet {@link RoomPortal}
 * une fois le graphe de rooms d'une instance construit.
 */
public record RoomTemplatePortal(HexCoordinate cell, String direction, UUID targetRoomTemplateId,
        HexCoordinate targetCell) {
}
