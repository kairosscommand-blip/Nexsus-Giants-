package com.nexusuniverse.giants;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds and identifies the "Miniature Crystal" item -- throw it at any mob to shrink it to the
 * smallest size this plugin's age-scaling system allows (GiantsConfig.minAge()/minScale(), the
 * exact same "tiniest possible" floor a natural age-1 spawn gets) and tame it permanently to
 * whoever threw it. Unlike NexusWarbeasts' Truce Crystal (which only stops a mob attacking its
 * giver) or NexusHatchlings' Hatchling Crystal (which grows a shrunk mob back up over real days),
 * a Miniature Crystal's mob STAYS tiny forever -- the whole point is a standing "miniature army"
 * of permanently pocket-sized pets, not a pet that eventually becomes normal-sized again.
 *
 * Identity is a PersistentDataContainer marker, not the display name or material -- same
 * anti-spoofing approach as every other Nexus "special item," so a renamed vanilla item can never
 * be mistaken for the real thing.
 */
public final class MiniatureCrystalItem {

    private final JavaPlugin plugin;
    private final NamespacedKey markerKey;

    private Material material = Material.AMETHYST_SHARD;
    private String displayName = "&dMiniature Crystal";
    private List<String> lore = new ArrayList<>();

    public MiniatureCrystalItem(JavaPlugin plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "miniature_crystal");
    }

    public void loadFromConfig() {
        String materialName = plugin.getConfig().getString("miniature.item.material", "AMETHYST_SHARD");
        Material parsed = Material.matchMaterial(materialName);
        this.material = parsed != null ? parsed : Material.AMETHYST_SHARD;
        this.displayName = plugin.getConfig().getString("miniature.item.display-name", "&dMiniature Crystal");
        this.lore = plugin.getConfig().getStringList("miniature.item.lore");
    }

    public ItemStack create(int amount) {
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(coloredLore);
            meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** True only for an item this plugin actually created -- checked via PDC, never display name
     * or material, so it can't be spoofed by renaming a plain amethyst shard in an anvil. */
    public boolean isMiniatureCrystal(ItemStack item) {
        if (item == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        Byte marker = meta.getPersistentDataContainer().get(markerKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }
}
