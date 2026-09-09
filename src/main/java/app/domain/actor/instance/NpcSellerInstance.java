package app.domain.actor.instance;

import java.util.Objects;
import java.util.UUID;

import app.domain.actor.AbstractNpc;
import app.domain.actor.system.SellSystem;
import app.domain.actor.template.NpcTemplate;
import app.domain.world.MapInstance;

public final class NpcSellerInstance extends AbstractNpc {

    private final SellSystem sellSystem;

    public NpcSellerInstance(UUID id, NpcTemplate template, MapInstance map) {
        super(id, template, map);
        this.sellSystem = new SellSystem(Objects.requireNonNull(template.shop()));
    }

    public SellSystem getSellSystem() {
        return sellSystem;
    }
}
