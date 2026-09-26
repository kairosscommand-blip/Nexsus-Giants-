package com.nexusuniverse.giants;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Scales loot quantity and dropped XP by however big the dying mob actually is, read live off its
 * current Attribute.SCALE value -- unchanged since v0.3.3. This works automatically with
 * v0.4.0's continuous age-driven sizing without any changes here: it never cared HOW a mob got its
 * scale (a rare fixed "giant" roll, an age-derived one, or an admin test-spawn), only what that
 * scale actually is at the moment it dies.
 */
public final class LootScaler implements Listener {

    private final GiantsConfig config;

    public LootScaler(GiantsConfig config) {
        this.config = config;
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (!config.lootScaleEnabled()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        AttributeInstance scaleAttribute = entity.getAttribute(Attribute.SCALE);
        if (scaleAttribute == null) {
            return;
        }
        double scale = scaleAttribute.getValue();
        if (scale <= 1.0) {
            return;
        }

        double lootMultiplier = 1.0 + (scale - 1.0) * config.lootScaleMultiplier();
        for (ItemStack item : event.getDrops()) {
            int scaled = (int) Math.round(item.getAmount() * lootMultiplier);
            item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(), scaled)));
        }

        double xpMultiplier = 1.0 + (scale - 1.0) * config.xpScaleMultiplier();
        event.setDroppedExp((int) Math.round(event.getDroppedExp() * xpMultiplier));
    }
}
