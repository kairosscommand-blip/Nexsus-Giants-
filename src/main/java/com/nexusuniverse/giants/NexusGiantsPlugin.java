package com.nexusuniverse.giants;

import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

public final class NexusGiantsPlugin extends JavaPlugin {

    private GiantsConfig config;
    private GiantMobManager mobManager;
    private RobotSkinManager robotSkinManager;
    private SpawnLockManager spawnLock;

    @Override
    public void onEnable() {
        config = new GiantsConfig(this);
        mobManager = new GiantMobManager(config, this);
        robotSkinManager = new RobotSkinManager(config);

        // Registration order matters here: GiantMobManager's onSpawn runs at the default (NORMAL)
        // priority and RobotSkinManager's runs at HIGH (see its own @EventHandler annotation), so
        // by the time the robot skin is built, this mob's real age-derived scale and name tag are
        // already in place.
        getServer().getPluginManager().registerEvents(mobManager, this);
        getServer().getPluginManager().registerEvents(new LootScaler(config), this);
        getServer().getPluginManager().registerEvents(robotSkinManager, this);
        getServer().getPluginManager().registerEvents(new RideableListener(config), this);
        getServer().getPluginManager().registerEvents(new SpawnLockGuard(config), this);

        getCommand("nexusgiants").setExecutor(new GiantsCommand(config, mobManager));

        getServer().getScheduler().runTaskTimer(this, () -> robotSkinManager.tickAll(getServer()), 20L, 20L);

        spawnLock = new SpawnLockManager(this, config);
        spawnLock.start();

        getLogger().info("NexusGiants enabled. age range=[" + config.minAge() + "-" + config.maxAge()
                + "], scale range=" + config.minScale() + "x-" + config.defaultScale() + "x (default type, see overrides), "
                + "overworld spawn-chance=" + Math.round(config.spawnChance(World.Environment.NORMAL) * 100) + "%, "
                + "nether spawn-chance=" + Math.round(config.spawnChance(World.Environment.NETHER) * 100) + "%, "
                + "end spawn-chance=" + Math.round(config.spawnChance(World.Environment.THE_END) * 100) + "%, "
                + "spawn-lock=" + (config.spawnLockEnabled() ? "ON" : "OFF"));
    }

    @Override
    public void onDisable() {
        if (spawnLock != null) {
            spawnLock.stop();
        }
        getLogger().info("NexusGiants disabled.");
    }

    public GiantsConfig getGiantsConfig() {
        return config;
    }

    public GiantMobManager getMobManager() {
        return mobManager;
    }

    public RobotSkinManager getRobotSkinManager() {
        return robotSkinManager;
    }
}
