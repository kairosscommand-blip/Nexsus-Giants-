package com.nexusuniverse.giants;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Continuously re-asserts doMobSpawning=true on every world, regardless of dimension, every single
 * tick -- the "correction, not prevention" half of spawn-lock (SpawnLockGuard is the other half:
 * it cancels the /gamerule command outright when it can). Unchanged since v0.3.3.
 */
public final class SpawnLockManager {

    private final JavaPlugin plugin;
    private final GiantsConfig config;
    private BukkitTask task;

    public SpawnLockManager(JavaPlugin plugin, GiantsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::enforce, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    private void enforce() {
        if (!config.spawnLockEnabled()) {
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            Boolean current = world.getGameRuleValue(GameRule.DO_MOB_SPAWNING);
            if (current == null || !current) {
                world.setGameRule(GameRule.DO_MOB_SPAWNING, true);
            }
        }
    }
}
