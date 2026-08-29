package dev.oakheart.oaktraps.listeners;

import dev.oakheart.oaktraps.managers.TrapManager;
import dev.oakheart.oaktraps.model.Trap;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Capture + release triggers:
 * <ul>
 *   <li>A mob stepping on an armed trap's pressure plate fires
 *       {@link EntityInteractEvent} → capture.</li>
 *   <li>Right-clicking a filled trap (barrel) releases the mob.</li>
 *   <li>A player physically stepping on a trap plate is suppressed (can't
 *       trip their own trap), and the barrel inventory never opens.</li>
 * </ul>
 */
public final class CageTriggerListener implements Listener {

    private final dev.oakheart.oaktraps.OakTraps plugin;
    private final TrapManager traps;

    public CageTriggerListener(dev.oakheart.oaktraps.OakTraps plugin, TrapManager traps) {
        this.plugin = plugin;
        this.traps = traps;
    }

    /**
     * Owner-or-protection gate for taking/releasing a trap. Left-click pickup is
     * a PlayerInteractEvent, which protection plugins do NOT cancel the way they
     * cancel BlockBreakEvent — without this, an untrusted visitor inside a claim
     * can pocket someone's armed trap or free their captured mob.
     */
    private boolean mayModify(Player player, Trap trap) {
        if (trap.getOwner() == null || trap.getOwner().equals(player.getUniqueId())) return true;
        return plugin.canPlaceCage(player, trap.getLocation());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityInteract(EntityInteractEvent event) {
        Block block = event.getBlock();
        Trap trap = traps.getTrap(block);
        if (trap == null || trap.isFilled()) return;

        // Only living mobs trigger the trap. Cancel everything else (dropped
        // items, arrows, XP orbs, ...) so they don't depress the plate.
        if (!(event.getEntity() instanceof LivingEntity living)) {
            event.setCancelled(true);
            return;
        }

        // NOT cancelled for mobs — let the pressure plate visibly depress. Capture
        // is then scheduled with a short delay so the trap is seen springing.
        traps.tryCapture(trap, living);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || !traps.isTrap(block)) return;

        Action action = event.getAction();
        if (action == Action.PHYSICAL) {
            event.setCancelled(true); // players can't trip a trap
            return;
        }

        Trap trap = traps.getTrap(block);
        if (trap == null) return;
        Player player = event.getPlayer();

        if (action == Action.LEFT_CLICK_BLOCK) {
            // An ARMED trap is a fake coral over a pressure plate, which can't be
            // broken normally — left-click picks it up instantly. A FILLED trap is
            // a real barrel: let it break like a normal barrel (handled by
            // CageBlockListener#onBreak when fully mined).
            if (trap.isFilled()) return;
            event.setCancelled(true);
            if (!mayModify(player, trap)) {
                plugin.getMessageManager().send(player, "interact-denied");
                return;
            }
            traps.removeTrap(trap, player.getGameMode() != GameMode.CREATIVE);
            return;
        }

        if (action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true); // do nothing useful; just don't place blocks on the trap
            if (trap.isFilled()) {
                if (!mayModify(player, trap)) {
                    plugin.getMessageManager().send(player, "interact-denied");
                    return;
                }
                traps.requestOpen(trap, player, player.getGameMode() != GameMode.CREATIVE);
            }
        }
    }
}
