package dev.oakheart.oaktraps.hooks;

import me.angeschossen.lands.api.LandsIntegration;
import me.angeschossen.lands.api.land.Area;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Lands integration: prevents a cage from capturing mobs inside a claim the
 * cage owner isn't trusted in (no mob-stealing), and blocks placing cages in
 * claims the player isn't trusted in.
 */
public final class LandsHook {

    private final LandsIntegration lands;
    private final Logger logger;

    public LandsHook(Plugin plugin, Logger logger) {
        this.logger = logger;
        LandsIntegration integration = null;
        try {
            integration = LandsIntegration.of(plugin);
        } catch (Throwable t) {
            logger.warning("Failed to bind Lands integration; Lands checks disabled.");
        }
        this.lands = integration;
    }

    /** @return true if {@code actor} may interact with mobs / build at the location. */
    public boolean isTrustedAt(UUID actor, Location location) {
        if (lands == null || actor == null || location.getWorld() == null) return true;
        try {
            Area area = lands.getArea(location);
            if (area == null) return true; // wilderness
            return area.isTrusted(actor);
        } catch (Throwable t) {
            return true; // fail-open
        }
    }
}
