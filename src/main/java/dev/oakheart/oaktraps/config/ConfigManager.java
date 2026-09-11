package dev.oakheart.oaktraps.config;

import dev.oakheart.oaktraps.OakTraps;
import dev.oakheart.oaktraps.model.ListMode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Plugin-specific wrapper around OakheartLib's {@code ConfigManager}. Loads,
 * evolves (merge defaults + sync comments), validates, and caches typed values.
 */
public class ConfigManager {

    private final OakTraps plugin;
    private final Logger logger;
    private final File configFile;
    private dev.oakheart.config.ConfigManager config;

    // General
    private boolean debugMode;

    // Trap item
    private Material trapMaterial;
    private String trapName;
    private boolean trapGlow;
    private List<String> trapLoreEmpty;
    private List<String> trapLoreFilled;

    // Capture rules
    private boolean allowedEnabled;
    private ListMode allowedMode;
    private Set<String> allowedList;
    private Set<NamespacedKey> protectedTags;
    private boolean canTrapTamed;
    private int captureTriggerDelay;
    private Particle captureParticle;
    private int captureParticleCount;
    private double captureParticleSpread;
    private double captureParticleSpeed;
    private boolean worldsEnabled;
    private ListMode worldsMode;
    private Set<String> worldsList;

    // Cage structure
    private Material pressurePlate;
    private BlockData filledBlock;
    private boolean visualEnabled;
    private double barrelY;
    private double barrelThickness;
    private double centerThickness;
    private double decorationY;
    private BlockData cageCenter;
    private BlockData cageNorth;
    private BlockData cageSouth;
    private BlockData cageEast;
    private BlockData cageWest;
    private BlockData cageDecoration;

    // Release
    private boolean breakOnRelease;
    private int releaseConfirmSeconds;
    private boolean releaseRequireSneak;

    // Protection
    private boolean respectWorldGuard;
    private boolean respectLands;
    private boolean allowPlaceInProtected;

    // MythicMobs
    private boolean mythicAllowedEnabled;
    private ListMode mythicAllowedMode;
    private Set<String> mythicAllowedList;

    // Crafting
    private boolean craftingEnabled;
    private List<String> craftingShape;
    private Map<Character, Material> craftingIngredients;

    public ConfigManager(OakTraps plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
    }

    public void load() {
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        try {
            this.config = dev.oakheart.config.ConfigManager.load(configFile.toPath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config.yml", e);
        }
        evolve();
        validate(this.config);
        cacheValues();
    }

    public boolean reload() {
        try {
            config.reload();
        } catch (IOException e) {
            logger.warning("Failed to reload config.yml: " + e.getMessage());
            return false;
        }
        if (!validate(config)) {
            logger.warning("Configuration reload failed validation. Keeping previous configuration.");
            return false;
        }
        cacheValues();
        logger.info("Configuration reloaded successfully.");
        return true;
    }

    /** mergeDefaults (new keys) + syncComments (doc updates), saving once if anything changed. */
    private void evolve() {
        try (var stream = plugin.getResource("config.yml")) {
            if (stream == null) return;
            var defaults = dev.oakheart.config.ConfigManager.fromStream(stream);
            boolean merged = config.mergeDefaults(defaults);
            boolean synced = false;
            try {
                File baseline = new File(plugin.getDataFolder(), ".oakheart/config-baseline.yml");
                baseline.getParentFile().mkdirs();
                synced = config.syncComments(defaults, baseline.toPath());
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to sync config comments", e);
            }
            if (merged || synced) {
                config.save();
                logger.info("Updated config.yml (merged new keys / synced comments).");
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to merge default config", e);
        }
    }

    private boolean validate(dev.oakheart.config.ConfigManager cfg) {
        List<String> warnings = new ArrayList<>();

        if (Material.matchMaterial(cfg.getString("trap-item.material", "BARREL")) == null) {
            warnings.add("trap-item.material is invalid; falling back to BARREL");
        }

        String particleName = cfg.getString("capture.particle.type", "CLOUD");
        if (!particleName.equalsIgnoreCase("NONE")) {
            Particle p = parseParticle(particleName);
            if (p == null) {
                warnings.add("capture.particle.type '" + particleName + "' is not a valid particle; the capture burst is disabled");
            } else if (p.getDataType() != Void.class) {
                // DUST/BLOCK/ITEM and friends need an extra data argument we have
                // no way to configure, and spawning them without it throws.
                warnings.add("capture.particle.type '" + particleName + "' requires extra particle data, which is not supported; the capture burst is disabled");
            }
        }

        if (!warnings.isEmpty()) {
            logger.warning("=== OakTraps Configuration Warnings ===");
            warnings.forEach(w -> logger.warning("  - " + w));
            logger.warning("=======================================");
        }
        return true; // warnings are non-fatal
    }

    private void cacheValues() {
        debugMode = config.getBoolean("debug", false);

        trapMaterial = material("trap-item.material", Material.BARREL);
        trapName = config.getString("trap-item.name", "<#E5C5D0>Mob Trap");
        trapGlow = config.getBoolean("trap-item.glow", true);
        trapLoreEmpty = config.getStringList("trap-item.lore-empty");
        trapLoreFilled = config.getStringList("trap-item.lore-filled");

        allowedEnabled = config.getBoolean("capture.allowed-entities.enabled", true);
        allowedMode = ListMode.fromString(config.getString("capture.allowed-entities.mode", "BLACKLIST"), ListMode.BLACKLIST);
        allowedList = lowerSet(config.getStringList("capture.allowed-entities.list"));
        canTrapTamed = config.getBoolean("capture.can-trap-tamed", true);
        protectedTags = parseKeys(config.getStringList("capture.protected-tags"));
        captureTriggerDelay = Math.max(0, config.getInt("capture.trigger-delay-ticks", 10));
        captureParticle = resolveCaptureParticle();
        captureParticleCount = Math.max(0, config.getInt("capture.particle.count", 12));
        captureParticleSpread = Math.max(0.0, config.getDouble("capture.particle.spread", 0.25));
        captureParticleSpeed = Math.max(0.0, config.getDouble("capture.particle.speed", 0.02));
        worldsEnabled = config.getBoolean("capture.worlds.enabled", false);
        worldsMode = ListMode.fromString(config.getString("capture.worlds.mode", "BLACKLIST"), ListMode.BLACKLIST);
        worldsList = lowerSet(config.getStringList("capture.worlds.list"));

        pressurePlate = material("cage.pressure-plate", Material.ACACIA_PRESSURE_PLATE);
        filledBlock = blockData("cage.filled-block", Material.BARREL, "[facing=down,open=true]");
        visualEnabled = config.getBoolean("cage.visual.enabled", true);
        barrelY = config.getDouble("cage.visual.barrel-y", 0.0);
        barrelThickness = config.getDouble("cage.visual.barrel-thickness", 0.03);
        centerThickness = config.getDouble("cage.visual.center-thickness", 0.02);
        decorationY = config.getDouble("cage.visual.decoration-y", -0.09);
        cageCenter = blockData("cage.visual.barrels.center", Material.BARREL, "[facing=down,open=true]");
        cageNorth = blockData("cage.visual.barrels.north", Material.BARREL, "[facing=north,open=false]");
        cageSouth = blockData("cage.visual.barrels.south", Material.BARREL, "[facing=south,open=false]");
        cageEast = blockData("cage.visual.barrels.east", Material.BARREL, "[facing=east,open=false]");
        cageWest = blockData("cage.visual.barrels.west", Material.BARREL, "[facing=west,open=false]");
        cageDecoration = blockData("cage.visual.decoration", Material.DEAD_BUBBLE_CORAL_FAN, "[waterlogged=false]");

        breakOnRelease = config.getBoolean("release.break-on-release", false);
        releaseConfirmSeconds = Math.max(0, config.getInt("release.confirm-seconds", 5));
        releaseRequireSneak = config.getBoolean("release.require-sneak", false);

        respectWorldGuard = config.getBoolean("protection.respect-worldguard", true);
        respectLands = config.getBoolean("protection.respect-lands", true);
        allowPlaceInProtected = config.getBoolean("protection.allow-place-in-protected", false);

        mythicAllowedEnabled = config.getBoolean("mythicmobs.allowed.enabled", false);
        mythicAllowedMode = ListMode.fromString(config.getString("mythicmobs.allowed.mode", "BLACKLIST"), ListMode.BLACKLIST);
        mythicAllowedList = lowerSet(config.getStringList("mythicmobs.allowed.list"));

        craftingEnabled = config.getBoolean("crafting.enabled", false);
        craftingShape = config.getStringList("crafting.shape");
        craftingIngredients = new LinkedHashMap<>();
        var ing = config.getSection("crafting.ingredients");
        if (ing != null) {
            for (String key : ing.getKeys(false)) {
                if (key.isEmpty()) continue;
                Material mat = Material.matchMaterial(ing.getString(key, ""));
                if (mat != null) {
                    craftingIngredients.put(key.charAt(0), mat);
                } else {
                    logger.warning("Invalid crafting ingredient material for key '" + key + "'");
                }
            }
        }
    }

    /**
     * The configured capture particle, or {@code null} when disabled, unknown, or
     * one of the data-bearing particles we can't spawn without extra arguments.
     * {@link #validate} has already warned about the last two cases.
     */
    private Particle resolveCaptureParticle() {
        String name = config.getString("capture.particle.type", "CLOUD");
        if (name.equalsIgnoreCase("NONE")) return null;
        Particle particle = parseParticle(name);
        if (particle == null || particle.getDataType() != Void.class) return null;
        return particle;
    }

    private static Particle parseParticle(String name) {
        try {
            return Particle.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Material material(String path, Material fallback) {
        Material mat = Material.matchMaterial(config.getString(path, fallback.name()));
        return mat != null ? mat : fallback;
    }

    private BlockData blockData(String path, Material matDefault, String dataDefault) {
        Material mat = material(path + ".material", matDefault);
        String data = config.getString(path + ".data", dataDefault);
        try {
            return Bukkit.createBlockData(mat, data);
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid block data '" + data + "' at " + path + "; using default.");
            try {
                return Bukkit.createBlockData(matDefault, dataDefault);
            } catch (IllegalArgumentException e2) {
                return matDefault.createBlockData();
            }
        }
    }

    /**
     * Parse {@code namespace:key} PDC keys, skipping (with a warning) any that
     * aren't valid. A typo here would otherwise silently drop a protection.
     */
    private Set<NamespacedKey> parseKeys(List<String> values) {
        Set<NamespacedKey> keys = new HashSet<>();
        for (String v : values) {
            if (v == null || v.isBlank()) continue;
            NamespacedKey key = NamespacedKey.fromString(v.trim().toLowerCase(Locale.ROOT));
            if (key == null) {
                logger.warning("capture.protected-tags entry '" + v + "' is not a valid namespace:key; ignoring it.");
                continue;
            }
            keys.add(key);
        }
        return keys;
    }

    private Set<String> lowerSet(List<String> values) {
        Set<String> set = new HashSet<>();
        for (String v : values) {
            if (v != null && !v.isBlank()) set.add(v.trim().toLowerCase(Locale.ROOT));
        }
        return set;
    }

    // --- Getters ---

    public dev.oakheart.config.ConfigManager getConfig() { return config; }
    public boolean isDebugMode() { return debugMode; }

    public Material getTrapMaterial() { return trapMaterial; }
    public String getTrapName() { return trapName; }
    public boolean isTrapGlow() { return trapGlow; }
    public List<String> getTrapLoreEmpty() { return trapLoreEmpty; }
    public List<String> getTrapLoreFilled() { return trapLoreFilled; }

    public Set<NamespacedKey> getProtectedTags() { return protectedTags; }
    public boolean isAllowedEnabled() { return allowedEnabled; }
    public ListMode getAllowedMode() { return allowedMode; }
    public Set<String> getAllowedList() { return allowedList; }
    public boolean isCanTrapTamed() { return canTrapTamed; }
    public int getCaptureTriggerDelay() { return captureTriggerDelay; }
    public Particle getCaptureParticle() { return captureParticle; }
    public int getCaptureParticleCount() { return captureParticleCount; }
    public double getCaptureParticleSpread() { return captureParticleSpread; }
    public double getCaptureParticleSpeed() { return captureParticleSpeed; }
    public boolean isWorldsEnabled() { return worldsEnabled; }
    public ListMode getWorldsMode() { return worldsMode; }
    public Set<String> getWorldsList() { return worldsList; }

    public Material getPressurePlate() { return pressurePlate; }
    public BlockData getFilledBlock() { return filledBlock; }
    public boolean isVisualEnabled() { return visualEnabled; }
    public double getBarrelY() { return barrelY; }
    public double getBarrelThickness() { return barrelThickness; }
    public double getCenterThickness() { return centerThickness; }
    public double getDecorationY() { return decorationY; }
    public BlockData getCageCenter() { return cageCenter; }
    public BlockData getCageNorth() { return cageNorth; }
    public BlockData getCageSouth() { return cageSouth; }
    public BlockData getCageEast() { return cageEast; }
    public BlockData getCageWest() { return cageWest; }
    public BlockData getCageDecoration() { return cageDecoration; }

    public boolean isBreakOnRelease() { return breakOnRelease; }
    public int getReleaseConfirmSeconds() { return releaseConfirmSeconds; }
    public boolean isReleaseRequireSneak() { return releaseRequireSneak; }
    public boolean isRespectWorldGuard() { return respectWorldGuard; }
    public boolean isRespectLands() { return respectLands; }
    public boolean isAllowPlaceInProtected() { return allowPlaceInProtected; }

    public boolean isMythicAllowedEnabled() { return mythicAllowedEnabled; }
    public ListMode getMythicAllowedMode() { return mythicAllowedMode; }
    public Set<String> getMythicAllowedList() { return mythicAllowedList; }

    public boolean isCraftingEnabled() { return craftingEnabled; }
    public List<String> getCraftingShape() { return craftingShape; }
    public Map<Character, Material> getCraftingIngredients() { return craftingIngredients; }
}
