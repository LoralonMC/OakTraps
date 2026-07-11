package dev.oakheart.oaktraps.hooks;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * WorldGuard integration: gates trap placement on the player's build rights at
 * the cage location (the BUILD flag / region membership).
 */
public final class WorldGuardHook {

    /** @return true if the player may build (place a cage) at the location. */
    public boolean canBuild(Player player, Location location) {
        try {
            LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
            RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
            return query.testBuild(BukkitAdapter.adapt(location), localPlayer);
        } catch (Throwable t) {
            return true; // fail-open
        }
    }
}
