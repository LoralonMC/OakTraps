package dev.oakheart.oaktraps.managers;

import dev.oakheart.message.MessageManager;
import dev.oakheart.oaktraps.OakTraps;
import dev.oakheart.oaktraps.api.events.TrapCaptureEvent;
import dev.oakheart.oaktraps.api.events.TrapOpenEvent;
import dev.oakheart.oaktraps.api.events.TrapPlaceEvent;
import dev.oakheart.oaktraps.api.events.TrapReleaseEvent;
import dev.oakheart.oaktraps.config.ConfigManager;
import dev.oakheart.oaktraps.model.CaptureData;
import dev.oakheart.oaktraps.model.Trap;
import dev.oakheart.oaktraps.storage.TrapStore;
import dev.oakheart.oaktraps.util.CaptureStorage;
import dev.oakheart.oaktraps.util.EntityCapture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core trap logic, mirroring NekoTraps: an armed trap is a pressure plate +
 * block-display cage; a filled trap is a barrel block; the captured mob is held
 * in the trap's item (PDC). The registry is the source of truth and is persisted
 * to a file ({@link TrapStore}).
 */
public final class TrapManager {

    private final OakTraps plugin;
    private final ConfigManager config;
    private final MessageManager messages;
    private final CaptureStorage storage;
    private final CageVisualManager visuals;
    private final TrapStore store;

    /** block location -> trap. */
    private final Map<Location, Trap> registry = new ConcurrentHashMap<>();

    private CaptureGuard captureGuard;
    private Function<LivingEntity, String> mythicTypeResolver;
    private BiConsumer<Entity, String> mythicReapply;

    public TrapManager(OakTraps plugin, ConfigManager config, MessageManager messages,
                       CaptureStorage storage, CageVisualManager visuals, TrapStore store) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.storage = storage;
        this.visuals = visuals;
        this.store = store;
    }

    /**
     * A player's un-confirmed release. {@code trapTime} pins it to the exact trap
     * that was clicked, so a trap picked up and replaced at the same spot between
     * the two clicks can't inherit the confirmation.
     */
    private record PendingRelease(Location location, long trapTime, long expiresAt) {}

    private final Map<UUID, PendingRelease> pendingReleases = new ConcurrentHashMap<>();

    // --- Persistence ---

    /** Load traps from disk, reapply their blocks, and spawn armed-cage visuals. */
    public void loadAll() {
        for (Trap trap : store.load()) {
            registry.put(trap.getLocation(), trap);
            applyBlock(trap);
            if (!trap.isFilled()) visuals.spawn(trap.getLocation());
        }
        plugin.getLogger().info("Loaded " + registry.size() + " trap(s).");
    }

    public void saveAll() {
        store.save(registry.values());
    }

    private void applyBlock(Trap trap) {
        Block block = trap.getLocation().getBlock();
        // applyPhysics=true so block changes go through the normal path that keeps
        // the POI registry in sync (a barrel is the Fisherman job-site POI block).
        if (trap.isFilled()) {
            block.setBlockData(config.getFilledBlock());
        } else {
            block.setType(config.getPressurePlate());
        }
    }

    // --- Trap items ---

    public ItemStack createTrapItem(boolean filled, CaptureData data) {
        ItemStack item = new ItemStack(config.getTrapMaterial());
        ItemMeta meta = item.getItemMeta();

        meta.displayName(messages.deserialize(config.getTrapName(), captureResolvers(data)));

        List<String> loreSource = filled ? config.getTrapLoreFilled() : config.getTrapLoreEmpty();
        List<Component> lore = new ArrayList<>();
        for (String line : loreSource) {
            lore.add(messages.deserialize(line, captureResolvers(data)));
        }
        if (!lore.isEmpty()) meta.lore(lore);
        if (config.isTrapGlow()) meta.setEnchantmentGlintOverride(true);

        storage.markTrap(meta.getPersistentDataContainer(), filled);
        if (filled && data != null) {
            storage.writeCapture(meta.getPersistentDataContainer(), data);
        }
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack getEmptyTrapItem() {
        return createTrapItem(false, null);
    }

    public boolean isTrapItem(ItemStack item) {
        return item != null && item.hasItemMeta()
                && storage.isTrap(item.getItemMeta().getPersistentDataContainer());
    }

    public boolean isTrapItemFilled(ItemStack item) {
        // "Filled" = the item actually carries a captured mob, regardless of flags.
        return isTrapItem(item) && storage.hasCapture(item.getItemMeta().getPersistentDataContainer());
    }

    // --- Placement ---

    public void place(Player player, Location location, ItemStack sourceItem) {
        if (sourceItem == null || sourceItem.isEmpty()) {
            plugin.getLogger().warning("Tried to place a trap from an empty item; aborting.");
            return;
        }
        Location loc = location.toBlockLocation();
        boolean filled = isTrapItemFilled(sourceItem);
        plugin.debug("place(): isTrap=" + isTrapItem(sourceItem) + " filled=" + filled
                + " hasMeta=" + (sourceItem != null && sourceItem.hasItemMeta()));

        ItemStack stored = sourceItem.clone();
        stored.setAmount(1);
        Trap trap = new Trap(loc, player.getUniqueId(), stored, filled);
        trap.setTrapTime(System.currentTimeMillis());

        TrapPlaceEvent event = new TrapPlaceEvent(player, loc, filled);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            // TrapPlaceListener already consumed the held item; a cancelled
            // event must refund it or the trap (mob included, if filled) is
            // silently destroyed.
            if (player.getGameMode() != org.bukkit.GameMode.CREATIVE && loc.getWorld() != null) {
                player.getInventory().addItem(stored.clone()).values().forEach(left ->
                        loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 0.5, 0.5), left));
            }
            return;
        }

        registry.put(loc, trap);
        applyBlock(trap);
        if (!filled) {
            visuals.spawn(loc);
            playSound(loc, Sound.BLOCK_WOODEN_DOOR_OPEN);
        } else {
            playSound(loc, Sound.BLOCK_WOODEN_DOOR_CLOSE);
        }
        saveAll();
        messages.send(player, filled ? "cage-placed-filled" : "cage-placed");
    }

    // --- Capture (mob steps on the plate -> EntityInteractEvent) ---

    /**
     * A mob stepped on the plate. We don't cancel the interact event (so the
     * plate visibly depresses), then capture after a short configurable delay so
     * the player sees the trap spring. The {@code capturing} lock prevents the
     * repeated plate events from scheduling more than one capture.
     */
    public void tryCapture(Trap trap, LivingEntity entity) {
        if (trap.isFilled() || trap.isCapturing()) return;
        if (!canCapture(trap, entity)) return;

        trap.setCapturing(true);
        int delay = config.getCaptureTriggerDelay();
        if (delay <= 0) {
            doCapture(trap, entity);
        } else {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> doCapture(trap, entity), delay);
        }
    }

    private void doCapture(Trap trap, LivingEntity entity) {
        try {
            if (trap.isFilled()) return;
            if (entity.isDead() || !entity.isValid()) return; // wandered off / died during the delay
            // The trap can be picked up (or its support broken) during the
            // trigger delay; capturing into an unregistered trap deletes the
            // mob permanently and places an orphaned barrel.
            if (registry.get(trap.getLocation().toBlockLocation()) != trap) return;

            // Cancellable event fires while the mob is still live and intact.
            TrapCaptureEvent event = new TrapCaptureEvent(entity, trap.getLocation(), trap.getOwner());
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return;

            // A captured mob shouldn't keep its lead — drop it and unleash first.
            if (entity.isLeashed()) {
                entity.getWorld().dropItemNaturally(entity.getLocation(), new ItemStack(Material.LEAD));
                entity.setLeashHolder(null);
            }

            String mythicType = mythicTypeResolver != null ? mythicTypeResolver.apply(entity) : null;
            CaptureData data = EntityCapture.capture(entity, mythicType != null, mythicType);

            trap.setItem(createTrapItem(true, data));
            trap.setFilled(true);
            trap.setTrapTime(System.currentTimeMillis());

            visuals.remove(trap.getLocation());
            entity.remove();
            applyBlock(trap); // plate -> barrel
            playSound(trap.getLocation(), Sound.BLOCK_WOODEN_DOOR_CLOSE);
            spawnCaptureBurst(trap.getLocation());
            plugin.debug("capture(): stored item filled=" + isTrapItemFilled(trap.getItem()));
            saveAll();
        } finally {
            trap.setCapturing(false);
        }
    }

    private boolean canCapture(Trap trap, LivingEntity entity) {
        if (entity.isDead() || !entity.isValid()) return false;

        if (config.isWorldsEnabled()) {
            String world = entity.getWorld().getName().toLowerCase(Locale.ROOT);
            if (!config.getWorldsMode().allows(config.getWorldsList().contains(world))) return false;
        }
        if (config.isAllowedEnabled()) {
            String type = entity.getType().getKey().getKey().toLowerCase(Locale.ROOT);
            if (!config.getAllowedMode().allows(config.getAllowedList().contains(type))) return false;
        }
        // Gate on ownership, not on the tamed flag. Some vanilla mobs report
        // isTamed() == true while belonging to nobody: Camel#isTamed is hardcoded
        // to true (a camel is rideable once saddled, never tamed by a player), and
        // a skeleton-horse trap sets its horses tamed without an owner. Those are
        // wild animals, so rejecting them for having no owner blocked every camel
        // from being caught at all.
        if (entity instanceof org.bukkit.entity.Tameable t && t.isTamed()
                && t.getOwnerUniqueId() != null) {
            if (!config.isCanTrapTamed()) return false;
            // Owned capture only ever applies to the trap owner's own pets:
            // a stranger's trap must never be able to steal someone's wolf/cat
            // (the serialized blob carries the tamed state to the captor).
            if (!t.getOwnerUniqueId().equals(trap.getOwner())) {
                return false;
            }
        }
        return captureGuard == null || captureGuard.allow(trap, entity);
    }

    // --- Release (right-click a filled barrel) ---

    /**
     * Player-initiated release. Applies the sneak requirement and the two-click
     * confirmation before handing off to {@link #open}; call {@code open}
     * directly only for releases that shouldn't be gated (API, admin tooling).
     */
    public void requestOpen(Trap trap, Player player, boolean returnItem) {
        if (!trap.isFilled() || trap.getItem() == null) return;

        if (config.isReleaseRequireSneak() && !player.isSneaking()) {
            messages.send(player, "release-sneak-required");
            return;
        }

        int seconds = config.getReleaseConfirmSeconds();
        if (seconds <= 0) {
            open(trap, player, returnItem);
            return;
        }

        Location loc = trap.getLocation().toBlockLocation();
        long now = System.currentTimeMillis();
        pendingReleases.values().removeIf(p -> p.expiresAt() <= now);

        PendingRelease pending = pendingReleases.get(player.getUniqueId());
        if (pending != null
                && pending.expiresAt() > now
                && pending.location().equals(loc)
                && pending.trapTime() == trap.getTrapTime()) {
            pendingReleases.remove(player.getUniqueId());
            open(trap, player, returnItem);
            return;
        }

        ItemMeta meta = trap.getItem().getItemMeta();
        CaptureData data = storage.readCapture(meta.getPersistentDataContainer());
        if (data == null) return;

        pendingReleases.put(player.getUniqueId(),
                new PendingRelease(loc, trap.getTrapTime(), now + seconds * 1000L));
        messages.send(player,
                config.isBreakOnRelease() ? "release-confirm-break" : "release-confirm",
                captureResolvers(data));
    }

    public void open(Trap trap, Player player, boolean returnItem) {
        if (!trap.isFilled() || trap.getItem() == null) return;
        ItemMeta meta = trap.getItem().getItemMeta();
        CaptureData data = storage.readCapture(meta.getPersistentDataContainer());
        if (data == null) return;

        TrapOpenEvent open = new TrapOpenEvent(player, trap.getLocation());
        Bukkit.getPluginManager().callEvent(open);
        if (open.isCancelled()) return;

        Location loc = trap.getLocation();
        if (loc.getWorld() == null) return;
        Location spawnAt = loc.clone().add(0.5, 0.2, 0.5);

        Entity restored = EntityCapture.deserialize(data.entityBytes(), loc.getWorld(), false);
        if (restored == null) return;
        // deserializeEntity creates the entity but does NOT add it to the world;
        // spawnAt actually places it.
        boolean spawned = restored.spawnAt(spawnAt, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM);
        if (!spawned) return; // keep the trap intact if the spawn was blocked
        if (player != null) {
            restored.setRotation(player.getLocation().getYaw() - 180.0f, 0f);
        }
        if (data.mythic() && mythicReapply != null) {
            mythicReapply.accept(restored, data.mythicType());
        }

        registry.remove(loc);
        clearPending(loc);
        loc.getBlock().setType(Material.AIR); // applyPhysics=true → proper light/render update
        visuals.remove(loc);
        visuals.refreshBlocks(loc);
        playSound(loc, Sound.BLOCK_WOODEN_DOOR_OPEN);

        // Return the empty trap unless it's a one-time-use trap (break-on-release).
        if (config.isBreakOnRelease()) {
            playSound(loc, Sound.ENTITY_ITEM_BREAK);
        } else if (returnItem) {
            loc.getWorld().dropItem(spawnAt, getEmptyTrapItem());
        }
        saveAll();

        Bukkit.getPluginManager().callEvent(new TrapReleaseEvent(restored, loc));
        if (player != null) messages.send(player, "mob-released", captureResolvers(data));
    }

    // --- Retrieve (break) ---

    /** Break a trap: drop its item (filled keeps the mob) and tear it down. */
    public void removeTrap(Trap trap, boolean dropItem) {
        Location loc = trap.getLocation();
        registry.remove(loc);
        clearPending(loc);
        visuals.remove(loc);
        loc.getBlock().setType(Material.AIR); // applyPhysics=true → proper light/render update
        visuals.refreshBlocks(loc);
        if (dropItem && loc.getWorld() != null && trap.getItem() != null) {
            loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 0.5, 0.5), trap.getItem().clone());
        }
        saveAll();
    }

    // --- Registry ---

    public boolean isTrap(Block block) {
        return block != null && registry.containsKey(block.getLocation().toBlockLocation());
    }

    public Trap getTrap(Block block) {
        return block == null ? null : registry.get(block.getLocation().toBlockLocation());
    }

    public Trap getTrap(Location loc) {
        return registry.get(loc.toBlockLocation());
    }

    public boolean hasTraps() {
        return !registry.isEmpty();
    }

    private static final BlockFace[] HORIZONTAL = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
    private static final int[][] FOOTPRINT =
            {{-1, -1}, {-1, 1}, {1, 1}, {1, -1}, {-2, 0}, {2, 0}, {0, -2}, {0, 2}};

    /**
     * Whether an armed cage fits at {@code loc} (NekoTraps' checkPlacingBlock):
     * solid floor under the plate, the four sides clear with solid floors, and
     * no other trap crowding the footprint.
     */
    public boolean canFit(Location loc) {
        Block block = loc.getBlock();
        if (!block.getRelative(0, -1, 0).getType().isSolid()) return false;
        for (BlockFace face : HORIZONTAL) {
            Block side = block.getRelative(face);
            if (!(side.isEmpty() || side.isReplaceable())) return false;
            if (!side.getRelative(0, -1, 0).getType().isSolid()) return false;
        }
        for (int[] off : FOOTPRINT) {
            Location o = block.getLocation().add(off[0], 0, off[1]).toBlockLocation();
            if (registry.containsKey(o)) return false;
        }
        return true;
    }

    public Collection<Trap> allTraps() {
        return registry.values();
    }

    /** Spawn cage visuals for armed traps in a chunk (called on chunk load). */
    public void refreshVisuals(int chunkX, int chunkZ, org.bukkit.World world) {
        for (Trap trap : registry.values()) {
            Location loc = trap.getLocation();
            if (trap.isFilled()) continue;
            if (!world.equals(loc.getWorld())) continue;
            if ((loc.getBlockX() >> 4) == chunkX && (loc.getBlockZ() >> 4) == chunkZ) {
                visuals.spawn(loc);
            }
        }
    }

    public void clearVisuals(int chunkX, int chunkZ, org.bukkit.World world) {
        for (Trap trap : registry.values()) {
            Location loc = trap.getLocation();
            if (!world.equals(loc.getWorld())) continue;
            if ((loc.getBlockX() >> 4) == chunkX && (loc.getBlockZ() >> 4) == chunkZ) {
                visuals.remove(loc);
            }
        }
    }

    // --- Hook wiring ---

    public interface CaptureGuard {
        boolean allow(Trap trap, LivingEntity entity);
    }

    public void setCaptureGuard(CaptureGuard guard) {
        this.captureGuard = guard;
    }

    public void setMythicTypeResolver(Function<LivingEntity, String> resolver) {
        this.mythicTypeResolver = resolver;
    }

    public void setMythicReapply(BiConsumer<Entity, String> reapply) {
        this.mythicReapply = reapply;
    }

    // --- Helpers ---

    /** Drop any un-confirmed releases pointing at a trap that no longer exists. */
    private void clearPending(Location loc) {
        Location key = loc.toBlockLocation();
        pendingReleases.values().removeIf(p -> p.location().equals(key));
    }

    /**
     * Short burst at the moment the trap springs. Deliberately tight: the player
     * lured the mob in from a block away and is watching, so a sustained effect
     * would hide the capture instead of punctuating it.
     */
    private void spawnCaptureBurst(Location loc) {
        Particle particle = config.getCaptureParticle();
        int count = config.getCaptureParticleCount();
        World world = loc.getWorld();
        if (particle == null || count <= 0 || world == null) return;
        double spread = config.getCaptureParticleSpread();
        world.spawnParticle(particle, loc.clone().add(0.5, 0.25, 0.5),
                count, spread, spread, spread, config.getCaptureParticleSpeed());
    }

    private void playSound(Location loc, Sound sound) {
        if (loc.getWorld() != null) loc.getWorld().playSound(loc, sound, 1.0f, 1.0f);
    }

    private TagResolver[] captureResolvers(CaptureData data) {
        if (data == null) {
            return new TagResolver[]{
                    Placeholder.unparsed("entity_type", "Empty"),
                    Placeholder.unparsed("entity_name", "Empty"),
                    Placeholder.unparsed("entity_variant", ""),
                    Placeholder.unparsed("entity_health", "0"),
                    Placeholder.unparsed("entity_max_health", "0"),
                    Placeholder.unparsed("entity_damage", "0")
            };
        }
        return new TagResolver[]{
                Placeholder.unparsed("entity_type", displayType(data)),
                Placeholder.unparsed("entity_name", data.displayName() != null ? data.displayName() : displayType(data)),
                Placeholder.unparsed("entity_variant", data.variant() != null ? data.variant() : ""),
                Placeholder.unparsed("entity_health", fmt(data.health())),
                Placeholder.unparsed("entity_max_health", fmt(data.maxHealth())),
                Placeholder.unparsed("entity_damage", fmt(data.damage()))
        };
    }

    /** Display name including variant, e.g. "Cold Pig" or "Plains Villager". */
    private String displayType(CaptureData data) {
        if (data.mythic() && data.mythicType() != null) return data.mythicType();
        String base = baseType(data.typeKey());
        return data.variant() != null ? data.variant() + " " + base : base;
    }

    private String baseType(String key) {
        int colon = key.indexOf(':');
        String path = colon >= 0 ? key.substring(colon + 1) : key;
        StringBuilder sb = new StringBuilder();
        for (String w : path.split("_")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private String fmt(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
