package com.nexusuniverse.giants;

import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

public final class NexusGiantsPlugin extends JavaPlugin {

    private GiantsConfig config;
    private GiantMobManager mobManager;
    private RobotSkinManager robotSkinManager;
    private SpawnLockManager spawnLock;
    private MiniatureTameTask miniatureTameTask;
    private MiniatureFollowTask miniatureFollowTask;

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

        getServer().getScheduler().runTaskTimer(this, () -> robotSkinManager.tickAll(getServer()), 20L, 20L);

        spawnLock = new SpawnLockManager(this, config);
        spawnLock.start();

        // Miniature Crystal: throw it at any mob (hostile, passive, or boss-tier alike) to shrink
        // it to this plugin's own "as small as it ever gets" floor and tame it permanently to
        // whoever threw it -- see MiniatureTameTask's own javadoc for why this is deliberately its
        // own separate item/mechanic rather than reusing NexusWarbeasts or NexusHatchlings.
        MiniatureCrystalItem miniatureCrystalItem = new MiniatureCrystalItem(this);
        miniatureCrystalItem.loadFromConfig();
        MiniatureRegistry miniatureRegistry = new MiniatureRegistry(this);
        MiniatureCrystalTracker miniatureTracker = new MiniatureCrystalTracker();

        getServer().getPluginManager().registerEvents(
                new MiniatureCrystalListener(this, miniatureCrystalItem, miniatureTracker), this);
        getServer().getPluginManager().registerEvents(new MiniatureProtectionListener(miniatureRegistry), this);

        miniatureTameTask = new MiniatureTameTask(this, miniatureTracker, miniatureRegistry, mobManager, config);
        miniatureTameTask.loadFromConfig();
        long miniatureScanInterval = Math.max(1, getConfig().getLong("miniature.mechanics.scan-interval-ticks", 10));
        miniatureTameTask.runTaskTimer(this, miniatureScanInterval, miniatureScanInterval);

        miniatureFollowTask = new MiniatureFollowTask(this, miniatureRegistry);
        miniatureFollowTask.loadFromConfig();
        long followInterval = Math.max(1, getConfig().getLong("miniature.follow-owner.check-interval-ticks", 20));
        miniatureFollowTask.runTaskTimer(this, followInterval, followInterval);

        new MiniatureRecipeRegistrar(this, miniatureCrystalItem).register();

        getCommand("nexusgiants").setExecutor(new GiantsCommand(config, mobManager, miniatureCrystalItem, miniatureRegistry));

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
        cancelQuietly(miniatureTameTask);
        cancelQuietly(miniatureFollowTask);
        getLogger().info("NexusGiants disabled.");
    }

    private void cancelQuietly(org.bukkit.scheduler.BukkitRunnable task) {
        if (task == null) {
            return;
        }
        try {
            task.cancel();
        } catch (IllegalStateException ignored) {
            // already not scheduled -- fine
        }
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
