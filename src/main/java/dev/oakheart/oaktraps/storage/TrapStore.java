package dev.oakheart.oaktraps.storage;

import dev.oakheart.oaktraps.model.Trap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Flat-file persistence for placed traps (like NekoTraps' registry-on-disk
 * model — pressure-plate traps have no block PDC to store into). One entry per
 * trap keyed by {@code world;x;y;z}; the trap item (which holds the captured
 * mob when filled) is stored as Base64 of {@link ItemStack#serializeAsBytes()}.
 */
public final class TrapStore {

    private final File file;
    private final Logger logger;

    public TrapStore(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "traps.yml");
        this.logger = logger;
    }

    public List<Trap> load() {
        List<Trap> traps = new ArrayList<>();
        if (!file.exists()) return traps;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("traps");
        if (root == null) return traps;

        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) continue;
            Location loc = parseKey(key);
            if (loc == null || loc.getWorld() == null) {
                // World not loaded / regenerated (e.g. weekly resource-world
                // reset): the trap's terrain is gone, so the entry is dropped —
                // loudly, because a filled trap here means a mob is lost.
                logger.warning("Dropping trap at '" + key + "': world not loaded"
                        + (sec.getBoolean("filled", false) ? " (trap was FILLED — captured mob is lost)" : ""));
                continue;
            }

            UUID owner = null;
            String ownerRaw = sec.getString("owner");
            if (ownerRaw != null) {
                try {
                    owner = UUID.fromString(ownerRaw);
                } catch (IllegalArgumentException ignored) {
                }
            }
            boolean filled = sec.getBoolean("filled", false);
            ItemStack item = null;
            String itemRaw = sec.getString("item");
            if (itemRaw != null) {
                try {
                    item = ItemStack.deserializeBytes(Base64.getDecoder().decode(itemRaw));
                } catch (Exception e) {
                    logger.warning("Failed to load trap item at " + key + "; dropping that trap.");
                    continue;
                }
            }
            Trap trap = new Trap(loc, owner, item, filled);
            trap.setTrapTime(sec.getLong("trap-time", 0L));
            traps.add(trap);
        }
        return traps;
    }

    public void save(Collection<Trap> traps) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Trap trap : traps) {
            // Per-trap isolation: keyOf() throws for a trap whose world was
            // unloaded (Location.getWorld() rejects unloaded worlds), and one
            // bad trap must not abort the whole file write — that exception
            // used to propagate out of place()/doCapture() mid-operation and
            // lose every OTHER trap's pending state.
            try {
                String key = keyOf(trap.getLocation());
                String path = "traps." + key + ".";
                if (trap.getOwner() != null) {
                    yaml.set(path + "owner", trap.getOwner().toString());
                }
                yaml.set(path + "filled", trap.isFilled());
                yaml.set(path + "trap-time", trap.getTrapTime());
                ItemStack item = trap.getItem();
                if (item != null && !item.isEmpty()) {
                    yaml.set(path + "item", Base64.getEncoder().encodeToString(item.serializeAsBytes()));
                }
            } catch (Exception e) {
                logger.warning("Skipping trap during save (world unloaded?): " + e.getMessage());
            }
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save traps.yml", e);
        }
    }

    private String keyOf(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    private Location parseKey(String key) {
        String[] parts = key.split(";");
        if (parts.length != 4) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        try {
            return new Location(world,
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
