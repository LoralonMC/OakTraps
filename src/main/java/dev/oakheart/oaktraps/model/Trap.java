package dev.oakheart.oaktraps.model;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * A placed trap. Like NekoTraps, the authoritative state lives in an in-memory
 * registry persisted to a file (see {@code storage.TrapStore}); the world block
 * (a pressure plate when armed, a barrel when filled) and the block-display cage
 * are just visuals.
 *
 * <p>The captured mob is stored inside {@link #getItem()}'s PDC (written by
 * {@code util.CaptureStorage}); an empty trap's item is a plain empty trap item.
 */
public final class Trap {

    private final Location location;
    private final UUID owner;
    private ItemStack item;
    private boolean filled;
    private long trapTime;

    /** Transient capture lock — guards against duplicate trigger events. */
    private boolean capturing;

    public Trap(Location location, UUID owner, ItemStack item, boolean filled) {
        this.location = location.toBlockLocation();
        this.owner = owner;
        this.item = item;
        this.filled = filled;
    }

    public Location getLocation() {
        return location;
    }

    public UUID getOwner() {
        return owner;
    }

    public ItemStack getItem() {
        return item;
    }

    public void setItem(ItemStack item) {
        this.item = item;
    }

    public boolean isFilled() {
        return filled;
    }

    public void setFilled(boolean filled) {
        this.filled = filled;
    }

    public long getTrapTime() {
        return trapTime;
    }

    public void setTrapTime(long trapTime) {
        this.trapTime = trapTime;
    }

    public boolean isCapturing() {
        return capturing;
    }

    public void setCapturing(boolean capturing) {
        this.capturing = capturing;
    }
}
