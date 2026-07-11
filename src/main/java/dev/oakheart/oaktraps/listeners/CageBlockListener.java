package dev.oakheart.oaktraps.listeners;

import dev.oakheart.oaktraps.managers.TrapManager;
import dev.oakheart.oaktraps.model.Trap;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * Breaking a trap drops its item into the world (a filled trap keeps the mob)
 * and tears it down. Also protects the trap block from explosions, pistons, and
 * liquid flow.
 */
public final class CageBlockListener implements Listener {

    private final TrapManager traps;

    public CageBlockListener(TrapManager traps) {
        this.traps = traps;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        boolean creative = event.getPlayer().getGameMode() == GameMode.CREATIVE;

        // Breaking the trap block itself.
        Trap self = traps.getTrap(block);
        if (self != null) {
            event.setCancelled(true);
            traps.removeTrap(self, !creative || self.isFilled());
            return;
        }

        // Breaking the block UNDER an armed trap: a pressure plate can't float,
        // so tear the trap down (drop it) instead of leaving a dead cage. A
        // filled trap is a solid barrel and survives, so leave it be.
        Trap above = traps.getTrap(block.getRelative(0, 1, 0));
        if (above != null && !above.isFilled()) {
            traps.removeTrap(above, !creative);
        }
    }

    /**
     * True for the trap block itself AND for the block directly under an armed
     * trap. An armed plate pops into a vanilla pressure-plate item when its
     * support is destroyed by physics (explosion/piston), losing the trap item
     * and leaving a ghost registry entry — so the support must be protected too.
     */
    private boolean protectsBlock(Block block) {
        if (traps.isTrap(block)) return true;
        Trap above = traps.getTrap(block.getRelative(0, 1, 0));
        return above != null && !above.isFilled();
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::protectsBlock);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::protectsBlock);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(this::protectsBlock)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(this::protectsBlock)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFromTo(BlockFromToEvent event) {
        Block to = event.getToBlock();
        if (traps.isTrap(to)) event.setCancelled(true);
    }
}
