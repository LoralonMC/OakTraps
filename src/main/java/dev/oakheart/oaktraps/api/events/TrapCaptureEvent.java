package dev.oakheart.oaktraps.api.events;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Fired when a placed cage is about to capture a lured mob, <b>before</b> any
 * irreversible change (the mob is still alive and present). Cancel to veto the
 * capture; the pressure-plate interaction is then suppressed too.
 */
public class TrapCaptureEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final LivingEntity entity;
    private final Location cage;
    private final UUID owner;
    private boolean cancelled;

    public TrapCaptureEvent(@NotNull LivingEntity entity, @NotNull Location cage, @Nullable UUID owner) {
        this.entity = entity;
        this.cage = cage;
        this.owner = owner;
    }

    /** The mob being captured (still alive at event time). */
    public @NotNull LivingEntity getEntity() {
        return entity;
    }

    /** The barrel (cage) location. */
    public @NotNull Location getCage() {
        return cage;
    }

    /** UUID of the player who placed the cage, if known. */
    public @Nullable UUID getOwner() {
        return owner;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
