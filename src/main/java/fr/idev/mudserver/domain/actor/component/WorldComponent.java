package fr.idev.mudserver.domain.actor.component;

import fr.idev.mudserver.domain.world.WorldInstance;

public class WorldComponent {

    public WorldInstance worldInstance;

    public WorldComponent(WorldInstance worldInstance) {
        this.worldInstance = worldInstance;
    }
}
