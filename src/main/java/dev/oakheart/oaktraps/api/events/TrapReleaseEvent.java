package dev.oakheart.oaktraps.api.events;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after a mob has been successfully released from a cage back into the
 * world. Informational (not cancellable) — the entity already exists.
 */
public class TrapReleaseEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Entity entity;
    private final Location cage;

    public TrapReleaseEvent(@NotNull Entity entity, @NotNull Location cage) {
        this.entity = entity;
        this.cage = cage;
    }

    /** The freshly spawned (restored) entity. */
    public @NotNull Entity getEntity() {
        return entity;
    }

    public @NotNull Location getCage() {
        return cage;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
