package dev.oakheart.oaktraps.hooks;

import com.nisovin.shopkeepers.api.ShopkeepersAPI;
import org.bukkit.entity.Entity;

/**
 * Shopkeepers integration: identifies shopkeeper entities so they are never
 * captured by a trap.
 */
public final class ShopkeepersHook {

    /** @return true if the entity is a Shopkeepers shopkeeper. */
    public boolean isShopkeeper(Entity entity) {
        try {
            if (!ShopkeepersAPI.isEnabled()) return false;
            return ShopkeepersAPI.getShopkeeperRegistry().getShopkeeperByEntity(entity) != null;
        } catch (Throwable t) {
            return false;
        }
    }
}
