package dev.oakheart.oaktraps.api.events;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player is about to place a trap cage. Cancel to prevent the
 * placement (the block placement is then cancelled too).
 */
public class TrapPlaceEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Location cage;
    private final boolean filled;
    private boolean cancelled;

    public TrapPlaceEvent(@NotNull Player player, @NotNull Location cage, boolean filled) {
        this.player = player;
        this.cage = cage;
        this.filled = filled;
    }

    public @NotNull Player getPlayer() {
        return player;
    }

    public @NotNull Location getCage() {
        return cage;
    }

    /** Whether the placed trap already holds a captured mob. */
    public boolean isFilled() {
        return filled;
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
