package com.nexusuniverse.giants;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.logging.Level;

/** Optional shapeless recipe for the Miniature Crystal, entirely config-driven -- same shape as
 * NexusWarbeasts'/NexusHatchlings' own crystal recipes. */
public final class MiniatureRecipeRegistrar {

    private final JavaPlugin plugin;
    private final MiniatureCrystalItem crystalItem;
    private final NamespacedKey recipeKey;

    public MiniatureRecipeRegistrar(JavaPlugin plugin, MiniatureCrystalItem crystalItem) {
        this.plugin = plugin;
        this.crystalItem = crystalItem;
        this.recipeKey = new NamespacedKey(plugin, "miniature_crystal");
    }

    public void register() {
        Bukkit.removeRecipe(recipeKey); // safe even if never registered -- lets /reload re-apply changed ingredients

        if (!plugin.getConfig().getBoolean("miniature.recipe.enabled", true)) {
            return;
        }

        List<String> ingredientNames = plugin.getConfig().getStringList("miniature.recipe.ingredients");
        if (ingredientNames.isEmpty()) {
            plugin.getLogger().warning("[NexusGiants] miniature.recipe.enabled is true but miniature.recipe.ingredients is empty -- skipping recipe registration.");
            return;
        }

        ItemStack result = crystalItem.create(1);
        ShapelessRecipe recipe = new ShapelessRecipe(recipeKey, result);
        for (String name : ingredientNames) {
            Material material = Material.matchMaterial(name);
            if (material == null) {
                plugin.getLogger().log(Level.WARNING, "[NexusGiants] Unknown material \"" + name + "\" in miniature.recipe.ingredients -- skipped it.");
                continue;
            }
            recipe.addIngredient(material);
        }

        Bukkit.addRecipe(recipe);
    }
}
