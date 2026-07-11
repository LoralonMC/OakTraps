package dev.oakheart.oaktraps.listeners;

import dev.oakheart.oaktraps.OakTraps;
import dev.oakheart.oaktraps.managers.CageVisualManager;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Makes cage brightness react instantly to light-source changes: when a block is
 * placed or broken (e.g. a torch), nearby trap cages are re-sampled a couple
 * ticks later (after the light engine has propagated the change).
 */
public final class CageLightListener implements Listener {

    private final OakTraps plugin;
    private final CageVisualManager visuals;

    public CageLightListener(OakTraps plugin, CageVisualManager visuals) {
        this.plugin = plugin;
        this.visuals = visuals;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        scheduleUpdate(event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        scheduleUpdate(event.getBlock().getLocation());
    }

    private void scheduleUpdate(Location loc) {
        // 2 ticks: let the light engine finish propagating before we sample.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> visuals.updateNear(loc), 2L);
    }
}
