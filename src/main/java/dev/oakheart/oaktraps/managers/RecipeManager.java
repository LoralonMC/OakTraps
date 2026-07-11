package dev.oakheart.oaktraps.managers;

import dev.oakheart.oaktraps.OakTraps;
import dev.oakheart.oaktraps.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Optional, config-gated crafting recipe for the empty trap item. Registered
 * once at enable; recipes persist for the server lifetime (not re-registered on
 * reload).
 */
public final class RecipeManager {

    private final OakTraps plugin;
    private final ConfigManager config;
    private final TrapManager traps;
    private final NamespacedKey key;

    public RecipeManager(OakTraps plugin, ConfigManager config, TrapManager traps) {
        this.plugin = plugin;
        this.config = config;
        this.traps = traps;
        this.key = new NamespacedKey(plugin, "trap_recipe");
    }

    public void register() {
        if (!config.isCraftingEnabled()) return;

        List<String> shape = config.getCraftingShape();
        if (shape.isEmpty() || shape.size() > 3) {
            plugin.getLogger().warning("crafting.shape must have 1-3 rows; recipe not registered.");
            return;
        }
        Map<Character, org.bukkit.Material> ingredients = config.getCraftingIngredients();
        if (ingredients.isEmpty()) {
            plugin.getLogger().warning("crafting.ingredients is empty; recipe not registered.");
            return;
        }

        try {
            ItemStack result = traps.createTrapItem(false, null);
            ShapedRecipe recipe = new ShapedRecipe(key, result);
            recipe.shape(shape.toArray(new String[0]));
            ingredients.forEach(recipe::setIngredient);

            Bukkit.removeRecipe(key); // idempotent across full plugin reloads
            Bukkit.addRecipe(recipe);
            plugin.getLogger().info("Registered trap crafting recipe.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to register trap crafting recipe — check crafting.shape/ingredients.", e);
        }
    }

    public void unregister() {
        Bukkit.removeRecipe(key);
    }
}
