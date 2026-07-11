package dev.oakheart.oaktraps.listeners;

import dev.oakheart.message.MessageManager;
import dev.oakheart.oaktraps.OakTraps;
import dev.oakheart.oaktraps.managers.TrapManager;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Placing a trap item cancels the vanilla block placement and hands off to
 * {@link TrapManager#place} a tick later, which builds the cage itself (a
 * pressure plate when empty, a barrel when filled) — mirroring NekoTraps.
 */
public final class TrapPlaceListener implements Listener {

    private final OakTraps plugin;
    private final TrapManager traps;
    private final MessageManager messages;

    public TrapPlaceListener(OakTraps plugin, TrapManager traps, MessageManager messages) {
        this.plugin = plugin;
        this.traps = traps;
        this.messages = messages;
    }

    // HIGHEST, not MONITOR: this handler cancels the event, and the Bukkit
    // contract forbids modifying outcome at MONITOR — block loggers reading at
    // MONITOR would record a placement that never happened, corrupting rollbacks.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!traps.isTrapItem(item)) return;

        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        event.setCancelled(true);

        if (!plugin.canPlaceCage(player, block.getLocation())) {
            messages.send(player, "place-denied");
            return;
        }

        // An armed cage needs room: solid floor + clear, solid-floored sides.
        if (!traps.isTrapItemFilled(item) && !traps.canFit(block.getLocation())) {
            messages.send(player, "place-no-fit");
            player.playSound(block.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Snapshot the trap item (with its PDC) BEFORE consuming the held stack —
        // getItemInHand() can share state with the inventory item, so consuming
        // first would empty this clone (which broke both persistence and the
        // filled-state detection).
        ItemStack toPlace = item.clone();
        toPlace.setAmount(1);

        // The place event is cancelled, so consume the item ourselves.
        if (player.getGameMode() != GameMode.CREATIVE) {
            EquipmentSlot hand = event.getHand();
            ItemStack inHand = hand == EquipmentSlot.OFF_HAND
                    ? player.getInventory().getItemInOffHand()
                    : player.getInventory().getItemInMainHand();
            inHand.setAmount(inHand.getAmount() - 1);
        }

        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> traps.place(player, block.getLocation(), toPlace), 1L);
    }
}
