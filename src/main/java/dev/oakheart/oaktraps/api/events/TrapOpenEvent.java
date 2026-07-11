package dev.oakheart.oaktraps.api.events;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player is about to open (release) a placed, filled cage,
 * <b>before</b> the mob is restored. Cancel to keep the cage closed.
 */
public class TrapOpenEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Location cage;
    private boolean cancelled;

    public TrapOpenEvent(@NotNull Player player, @NotNull Location cage) {
        this.player = player;
        this.cage = cage;
    }

    public @NotNull Player getPlayer() {
        return player;
    }

    public @NotNull Location getCage() {
        return cage;
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
