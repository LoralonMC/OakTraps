package dev.oakheart.oaktraps;

import dev.oakheart.message.MessageManager;
import dev.oakheart.oaktraps.commands.TrapsCommand;
import dev.oakheart.oaktraps.config.ConfigManager;
import dev.oakheart.oaktraps.hooks.LandsHook;
import dev.oakheart.oaktraps.hooks.MythicMobsHook;
import dev.oakheart.oaktraps.hooks.ShopkeepersHook;
import dev.oakheart.oaktraps.hooks.WorldGuardHook;
import dev.oakheart.oaktraps.listeners.CageBlockListener;
import dev.oakheart.oaktraps.listeners.CageLightListener;
import dev.oakheart.oaktraps.listeners.CageReconcileListener;
import dev.oakheart.oaktraps.listeners.CageTriggerListener;
import dev.oakheart.oaktraps.listeners.TrapPlaceListener;
import dev.oakheart.oaktraps.managers.CageVisualManager;
import dev.oakheart.oaktraps.managers.RecipeManager;
import dev.oakheart.oaktraps.managers.TrapManager;
import dev.oakheart.oaktraps.storage.TrapStore;
import dev.oakheart.oaktraps.util.CaptureStorage;
import dev.oakheart.util.DebugLogger;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.logging.Level;

public final class OakTraps extends JavaPlugin {

    /** bStats plugin id — replace with the real id after registering the plugin. */
    private static final int BSTATS_ID = 00000;

    private ConfigManager configManager;
    private MessageManager messageManager;
    private DebugLogger debugLogger;
    private CaptureStorage captureStorage;
    private CageVisualManager cageVisualManager;
    private TrapStore trapStore;
    private TrapManager trapManager;
    private RecipeManager recipeManager;

    private WorldGuardHook worldGuardHook;
    private LandsHook landsHook;
    private ShopkeepersHook shopkeepersHook;
    private MythicMobsHook mythicMobsHook;

    @Override
    public void onEnable() {
        try {
            initializeComponents();
            wireHooks();
            registerListeners();
            registerCommands();
            registerRecipes();
            trapManager.loadAll();
            cageVisualManager.startBrightnessTask();
            initializeMetrics();
            getLogger().info("OakTraps v" + getPluginMeta().getVersion() + " has been enabled!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable OakTraps", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (recipeManager != null) recipeManager.unregister();
        if (trapManager != null) trapManager.saveAll();
        if (cageVisualManager != null) cageVisualManager.shutdown();
        getLogger().info("OakTraps has been disabled!");
    }

    private void initializeComponents() {
        configManager = new ConfigManager(this);
        configManager.load();

        debugLogger = new DebugLogger(getLogger(), configManager::isDebugMode);

        messageManager = new MessageManager(this, getLogger());
        messageManager.load();

        captureStorage = new CaptureStorage(this);
        cageVisualManager = new CageVisualManager(this, configManager);
        trapStore = new TrapStore(getDataFolder(), getLogger());
        trapManager = new TrapManager(this, configManager, messageManager, captureStorage, cageVisualManager, trapStore);
    }

    /** Create present integration hooks and install capture guards. */
    private void wireHooks() {
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            worldGuardHook = new WorldGuardHook();
            getLogger().info("WorldGuard found! Region protection enabled.");
        }
        if (Bukkit.getPluginManager().getPlugin("Lands") != null) {
            landsHook = new LandsHook(this, getLogger());
            getLogger().info("Lands found! Claim protection enabled.");
        }
        if (Bukkit.getPluginManager().getPlugin("Shopkeepers") != null) {
            shopkeepersHook = new ShopkeepersHook();
            getLogger().info("Shopkeepers found! Shopkeeper capture protection enabled.");
        }
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") != null) {
            mythicMobsHook = new MythicMobsHook(getLogger());
            getLogger().info("MythicMobs found! Custom mob capture enabled.");
        }

        trapManager.setMythicTypeResolver(entity ->
                mythicMobsHook != null ? mythicMobsHook.getType(entity) : null);

        trapManager.setCaptureGuard((cage, entity) -> {
            if (shopkeepersHook != null && shopkeepersHook.isShopkeeper(entity)) {
                return false;
            }
            if (mythicMobsHook != null && configManager.isMythicAllowedEnabled()) {
                String type = mythicMobsHook.getType(entity);
                if (type != null) {
                    boolean inList = configManager.getMythicAllowedList().contains(type.toLowerCase(Locale.ROOT));
                    if (!configManager.getMythicAllowedMode().allows(inList)) return false;
                }
            }
            if (configManager.isRespectLands() && landsHook != null
                    && !landsHook.isTrustedAt(cage.getOwner(), entity.getLocation())) {
                return false;
            }
            return true;
        });
    }

    private void registerListeners() {
        var pm = getServer().getPluginManager();
        pm.registerEvents(new TrapPlaceListener(this, trapManager, messageManager), this);
        pm.registerEvents(new CageTriggerListener(this, trapManager), this);
        pm.registerEvents(new CageBlockListener(trapManager), this);
        pm.registerEvents(new CageReconcileListener(trapManager), this);
        pm.registerEvents(new CageLightListener(this, cageVisualManager), this);
    }

    private void registerCommands() {
        new TrapsCommand(this, trapManager, messageManager).register();
    }

    private void registerRecipes() {
        recipeManager = new RecipeManager(this, configManager, trapManager);
        recipeManager.register();
    }

    private void initializeMetrics() {
        if (BSTATS_ID > 0) {
            new Metrics(this, BSTATS_ID);
        }
    }

    /** Whether a player may place a cage at a location (WorldGuard + Lands). */
    public boolean canPlaceCage(Player player, Location location) {
        if (configManager.isRespectWorldGuard() && worldGuardHook != null
                && !worldGuardHook.canBuild(player, location)) {
            return false;
        }
        if (configManager.isRespectLands() && !configManager.isAllowPlaceInProtected() && landsHook != null
                && !landsHook.isTrustedAt(player.getUniqueId(), location)) {
            return false;
        }
        return true;
    }

    public void reloadPluginConfig() {
        if (!configManager.reload()) {
            throw new RuntimeException("Configuration reload failed validation");
        }
        messageManager.reload();
    }

    public void debug(String message) {
        if (debugLogger != null) debugLogger.log(message);
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public TrapManager getTrapManager() {
        return trapManager;
    }
}
