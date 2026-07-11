package dev.oakheart.oaktraps.managers;

import dev.oakheart.oaktraps.OakTraps;
import dev.oakheart.oaktraps.config.ConfigManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The armed-trap "cage" visual: five flattened {@link BlockDisplay} barrels
 * (centre + N/S/E/W) forming a flat base, plus a central coral decoration. Each
 * piece's brightness is kept live-matched to the light at its own block (so the
 * cage dims with day/night and reacts to torches), updated by a repeating task.
 *
 * <p>Displays are non-persistent and are respawned from the trap registry on
 * chunk load.
 */
public final class CageVisualManager {

    private final OakTraps plugin;
    private final ConfigManager config;
    private final Map<Location, List<CagePiece>> displays = new ConcurrentHashMap<>();
    private BukkitTask brightnessTask;

    public CageVisualManager(OakTraps plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Slow safety poll for light changes that don't fire a block event (lava
     * flow, fire spread, ...). Torches/blocks are handled instantly by
     * {@link #updateNear} via the light listener; day/night dimming is automatic
     * (the client applies the day/night curve to the sky-light component).
     */
    public void startBrightnessTask() {
        if (brightnessTask != null) return;
        brightnessTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::updateBrightness, 100L, 100L);
    }

    /** Spawn (or refresh) the cage visual for an armed trap at {@code loc}. */
    public void spawn(Location loc) {
        if (!config.isVisualEnabled()) return;
        World world = loc.getWorld();
        if (world == null) return;
        remove(loc);

        Location trap = loc.toBlockLocation();
        double bx = trap.getBlockX();
        double by = trap.getBlockY();
        double bz = trap.getBlockZ();
        double y = by + config.getBarrelY();
        double sideT = config.getBarrelThickness();
        double centerT = config.getCenterThickness();

        // Each piece samples its light from a neighbouring block at the trap's
        // surface level (so the centre and the four sides can differ, and none
        // sample a solid block below the trap).
        List<CagePiece> pieces = new ArrayList<>(6);
        addPiece(pieces, world, bx, y, bz, config.getCageCenter(), centerT, trap);
        addPiece(pieces, world, bx + 0.001, y, bz - 0.99, config.getCageNorth(), sideT, trap.clone().add(0, 0, -1));
        addPiece(pieces, world, bx + 0.001, y, bz + 0.99, config.getCageSouth(), sideT, trap.clone().add(0, 0, 1));
        addPiece(pieces, world, bx + 0.99, y, bz + 0.001, config.getCageEast(), sideT, trap.clone().add(1, 0, 0));
        addPiece(pieces, world, bx - 0.99, y, bz + 0.001, config.getCageWest(), sideT, trap.clone().add(-1, 0, 0));

        // Central coral "teeth" — a real display so it shows on top of the base
        // without hiding the pressure plate.
        BlockData decoration = config.getCageDecoration();
        if (decoration != null && !decoration.getMaterial().isAir()) {
            Location decoPos = new Location(world, bx, by + config.getDecorationY(), bz);
            BlockDisplay deco = world.spawn(decoPos, BlockDisplay.class, d -> {
                d.setBlock(decoration);
                d.setPersistent(false);
                d.setDisplayWidth(2.0f);
                d.setDisplayHeight(2.0f);
            });
            pieces.add(new CagePiece(deco.getUniqueId(), trap.clone()));
        }

        displays.put(trap, pieces);
        updatePieces(pieces); // set initial brightness immediately
    }

    private void addPiece(List<CagePiece> pieces, World world, double x, double y, double z,
                          BlockData block, double thickness, Location sample) {
        Transformation transform = new Transformation(
                new Vector3f(0f, 0f, 0f),
                new AxisAngle4f(0f, 0f, 0f, 1f),
                new Vector3f(1f, (float) thickness, 1f),
                new AxisAngle4f(0f, 0f, 0f, 1f));
        BlockDisplay display = world.spawn(new Location(world, x, y, z), BlockDisplay.class, d -> {
            d.setBlock(block);
            d.setPersistent(false);
            d.setTransformation(transform);
            // Enlarge the cull box so the flattened barrels don't disappear at angles.
            d.setDisplayWidth(2.0f);
            d.setDisplayHeight(2.0f);
        });
        pieces.add(new CagePiece(display.getUniqueId(), sample));
    }

    /** Re-sample every cage piece's block light and update brightness if it changed. */
    public void updateBrightness() {
        for (List<CagePiece> pieces : displays.values()) {
            updatePieces(pieces);
        }
    }

    /**
     * Immediately re-sample traps within block-light range of a changed block
     * (e.g. a torch placed/broken), so their brightness updates instantly.
     */
    public void updateNear(Location changed) {
        if (displays.isEmpty() || changed.getWorld() == null) return;
        int cx = changed.getBlockX();
        int cy = changed.getBlockY();
        int cz = changed.getBlockZ();
        for (Map.Entry<Location, List<CagePiece>> entry : displays.entrySet()) {
            Location trap = entry.getKey();
            if (!changed.getWorld().equals(trap.getWorld())) continue;
            if (Math.abs(trap.getBlockX() - cx) <= 16
                    && Math.abs(trap.getBlockY() - cy) <= 16
                    && Math.abs(trap.getBlockZ() - cz) <= 16) {
                updatePieces(entry.getValue());
            }
        }
    }

    private void updatePieces(List<CagePiece> pieces) {
        for (CagePiece piece : pieces) {
            if (!(plugin.getServer().getEntity(piece.id) instanceof BlockDisplay display) || !display.isValid()) {
                continue;
            }
            if (piece.sample.getWorld() == null || !piece.sample.isChunkLoaded()) continue;
            Block block = piece.sample.getBlock();
            int blockLight = block.getLightFromBlocks();
            int skyLight = block.getLightFromSky();
            if (piece.lastBlock != blockLight || piece.lastSky != skyLight) {
                display.setBrightness(new Display.Brightness(blockLight, skyLight));
                piece.lastBlock = blockLight;
                piece.lastSky = skyLight;
            }
        }
    }

    /**
     * Re-send the real block data in a 3x3x3 area around the trap to everyone in
     * the world, forcing the client to re-render (fixes the grass-tint glitch
     * after a trap breaks). Delayed so it runs after the removal update flushes.
     */
    public void refreshBlocks(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;
        Location base = loc.toBlockLocation();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            List<BlockState> states = new ArrayList<>(27);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        states.add(base.clone().add(dx, dy, dz).getBlock().getState());
                    }
                }
            }
            for (Player player : world.getPlayers()) {
                player.sendBlockChanges(states);
            }
        }, 2L);
    }

    /** Remove the cage visual at {@code loc}, if any. */
    public void remove(Location loc) {
        List<CagePiece> pieces = displays.remove(loc.toBlockLocation());
        if (pieces == null) return;
        for (CagePiece piece : pieces) {
            Entity entity = plugin.getServer().getEntity(piece.id);
            if (entity != null) entity.remove();
        }
    }

    public boolean hasVisual(Location loc) {
        return displays.containsKey(loc.toBlockLocation());
    }

    /** Remove every cage visual we own and stop the brightness task (on disable). */
    public void shutdown() {
        if (brightnessTask != null) {
            brightnessTask.cancel();
            brightnessTask = null;
        }
        for (List<CagePiece> pieces : displays.values()) {
            for (CagePiece piece : pieces) {
                Entity entity = plugin.getServer().getEntity(piece.id);
                if (entity != null) entity.remove();
            }
        }
        displays.clear();
    }

    /** One cage display + the block whose light it should mirror. */
    private static final class CagePiece {
        final UUID id;
        final Location sample;
        int lastBlock = -1;
        int lastSky = -1;

        CagePiece(UUID id, Location sample) {
            this.id = id;
            this.sample = sample;
        }
    }
}
