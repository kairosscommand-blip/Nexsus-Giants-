package com.nexusuniverse.giants;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;

/**
 * A tamed miniature never targets or attacks its own owner -- permanently, no retaliation window
 * (unlike NexusWarbeasts' neutralized mobs, a tamed pet doesn't need one: it's not "currently
 * peaceful," it's yours). Everyone else is untouched -- a tamed mini zombie still fights other
 * players/mobs normally, which is half the fun of a "miniature army." Two-layer defense
 * (EntityTargetEvent + EntityDamageByEntityEvent) is the same pattern used everywhere else in the
 * Nexus family a mob must never be allowed to harm a specific player.
 */
public final class MiniatureProtectionListener implements Listener {

    private final MiniatureRegistry registry;

    public MiniatureProtectionListener(MiniatureRegistry registry) {
        this.registry = registry;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTarget(EntityTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if (!(event.getTarget() instanceof Player player)) {
            return;
        }
        if (registry.isTamedMiniature(mob) && registry.isOwnedBy(mob, player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity damager)) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (registry.isTamedMiniature(damager) && registry.isOwnedBy(damager, victim.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
