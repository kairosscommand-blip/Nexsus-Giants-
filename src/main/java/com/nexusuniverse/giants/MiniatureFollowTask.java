package com.nexusuniverse.giants;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Keeps every tamed miniature near its owner -- a real vanilla-style follow via Mob#getPathfinder()
 * when it's just lagging behind, or a straight teleport when it's fallen too far behind (or the
 * owner changed worlds), mirroring the exact "pathfinder with a teleport fallback" pattern
 * NexusHatchlings already uses for its own grown/growing pets. Only scans already-loaded chunks
 * (World#getEntities() never force-loads anything) -- consistent with this project's usual
 * discipline elsewhere (see NexusBloodline), so a mini army in an unloaded part of the world simply
 * waits where it is rather than forcing chunks to stay loaded just to keep tabs on it.
 */
public final class MiniatureFollowTask extends BukkitRunnable {

    private final JavaPlugin plugin;
    private final MiniatureRegistry registry;

    private boolean enabled = true;
    private double teleportDistance = 12.0;
    private double walkDistance = 3.0;
    private double speed = 1.0;

    public MiniatureFollowTask(JavaPlugin plugin, MiniatureRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public void loadFromConfig() {
        enabled = plugin.getConfig().getBoolean("miniature.follow-owner.enabled", true);
        teleportDistance = plugin.getConfig().getDouble("miniature.follow-owner.teleport-distance", 12.0);
        walkDistance = plugin.getConfig().getDouble("miniature.follow-owner.walk-distance", 3.0);
        speed = plugin.getConfig().getDouble("miniature.follow-owner.speed", 1.0);
    }

    @Override
    public void run() {
        if (!enabled) {
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof LivingEntity living) || !registry.isTamedMiniature(living)) {
                    continue;
                }
                java.util.UUID ownerId = registry.ownerOf(living);
                if (ownerId == null) {
                    continue;
                }
                Player owner = Bukkit.getPlayer(ownerId);
                if (owner == null) {
                    continue; // offline -- stays put until they're back
                }

                Location ownerLocation = owner.getLocation();
                if (ownerLocation.getWorld() == null || living.getWorld() == null) {
                    continue;
                }

                if (!ownerLocation.getWorld().equals(living.getWorld())) {
                    living.teleport(nearOwner(ownerLocation));
                    continue;
                }

                double distance = living.getLocation().distance(ownerLocation);
                if (distance > teleportDistance) {
                    living.teleport(nearOwner(ownerLocation));
                } else if (distance > walkDistance && living instanceof Mob mob) {
                    mob.getPathfinder().moveTo(ownerLocation, speed);
                }
            }
        }
    }

    /** A small random offset so multiple minis don't all try to stack on the owner's exact block. */
    private Location nearOwner(Location ownerLocation) {
        double offsetX = (Math.random() - 0.5) * 3.0;
        double offsetZ = (Math.random() - 0.5) * 3.0;
        return ownerLocation.clone().add(offsetX, 0, offsetZ);
    }
}
