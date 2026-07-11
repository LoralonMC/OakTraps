package dev.oakheart.oaktraps.listeners;

import dev.oakheart.oaktraps.managers.TrapManager;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

/**
 * Armed-trap cage visuals are non-persistent block-display entities, so they
 * must be respawned when a chunk loads and removed when it unloads. The trap
 * registry (persisted to disk) is the source of truth.
 */
public final class CageReconcileListener implements Listener {

    private final TrapManager traps;

    public CageReconcileListener(TrapManager traps) {
        this.traps = traps;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!traps.hasTraps()) return;
        Chunk chunk = event.getChunk();
        traps.refreshVisuals(chunk.getX(), chunk.getZ(), chunk.getWorld());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (!traps.hasTraps()) return;
        Chunk chunk = event.getChunk();
        traps.clearVisuals(chunk.getX(), chunk.getZ(), chunk.getWorld());
    }
}
