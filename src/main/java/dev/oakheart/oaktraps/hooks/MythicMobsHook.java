package dev.oakheart.oaktraps.hooks;

import org.bukkit.entity.Entity;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MythicMobs integration via reflection (the MythicMobs artifact isn't always
 * resolvable at build time, and reflection keeps this an optional soft hook).
 *
 * <p>Used only to identify a mob as a MythicMob and read its internal type for
 * the allow/deny list. The mob's MythicMobs data itself rides along in the
 * serialized entity blob, so MythicMobs re-recognizes released mobs on its own.
 */
public final class MythicMobsHook {

    private final Logger logger;
    private Object apiHelper;
    private Method isMythicMob;
    private Method getMythicMobInstance;
    private boolean warned;

    public MythicMobsHook(Logger logger) {
        this.logger = logger;
        try {
            Class<?> mythicBukkit = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object inst = mythicBukkit.getMethod("inst").invoke(null);
            this.apiHelper = inst.getClass().getMethod("getAPIHelper").invoke(inst);
            this.isMythicMob = apiHelper.getClass().getMethod("isMythicMob", Entity.class);
            this.getMythicMobInstance = apiHelper.getClass().getMethod("getMythicMobInstance", Entity.class);
        } catch (Throwable t) {
            logger.warning("Could not bind MythicMobs API; MythicMobs capture rules disabled.");
            this.apiHelper = null;
        }
    }

    public boolean isMythic(Entity entity) {
        if (apiHelper == null) return false;
        try {
            return (boolean) isMythicMob.invoke(apiHelper, entity);
        } catch (Throwable t) {
            warnOnce(t);
            return false;
        }
    }

    /** @return the MythicMob internal name, or {@code null} if not a MythicMob. */
    public String getType(Entity entity) {
        if (apiHelper == null) return null;
        try {
            Object activeMob = getMythicMobInstance.invoke(apiHelper, entity);
            if (activeMob == null) return null;
            Object type = activeMob.getClass().getMethod("getType").invoke(activeMob);
            if (type == null) return null;
            Object name = type.getClass().getMethod("getInternalName").invoke(type);
            return name != null ? name.toString() : null;
        } catch (Throwable t) {
            warnOnce(t);
            return null;
        }
    }

    private void warnOnce(Throwable t) {
        if (warned) return;
        warned = true;
        logger.log(Level.WARNING, "MythicMobs reflection call failed; treating mobs as vanilla.", t);
    }
}
