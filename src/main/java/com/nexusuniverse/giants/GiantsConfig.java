package com.nexusuniverse.giants;

import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;

/**
 * All of NexusGiants' config.yml reading in one place -- always reads live off the current
 * FileConfiguration (reload() just re-reads it and rebuilds the two cached type sets below), same
 * "no stale cache" approach used by every other Nexus plugin's config wrapper.
 */
public class GiantsConfig {

    private final JavaPlugin plugin;
    private final Set<EntityType> excluded = new HashSet<>();
    private final Set<EntityType> robotTypes = new HashSet<>();

    public GiantsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
        loadExcluded();
        loadRobotTypes();
    }

    private void loadExcluded() {
        excluded.clear();
        for (String s : plugin.getConfig().getStringList("excluded")) {
            try {
                excluded.add(EntityType.valueOf(s.toUpperCase()));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Unknown entity type in 'excluded': " + s);
            }
        }
    }

    private void loadRobotTypes() {
        robotTypes.clear();
        for (String s : plugin.getConfig().getStringList("robots.types")) {
            try {
                robotTypes.add(EntityType.valueOf(s.toUpperCase()));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Unknown entity type in 'robots.types': " + s);
            }
        }
    }

    public boolean isExcluded(EntityType type) {
        return excluded.contains(type);
    }

    public boolean robotsEnabled() {
        return plugin.getConfig().getBoolean("robots.enabled", true);
    }

    public boolean isRobotType(EntityType type) {
        return robotTypes.contains(type);
    }

    public String robotNamePrefix() {
        return plugin.getConfig().getString("robots.name-prefix", "§7§l[UNIT] ");
    }

    /** The scale a mob of any type reaches at its OLDEST (age-scaling.max-age) if it has no entry
     * under "overrides" -- also the scale used for a plain admin test-spawn when age-scaling is
     * disabled entirely. */
    public double defaultScale() {
        return plugin.getConfig().getDouble("default-scale", 3.0);
    }

    /** The scale {@code type} reaches at its OLDEST (age-scaling.max-age) -- "overrides.<type>" if
     * present, otherwise defaultScale(). This used to be the single fixed "giant size" for a type;
     * as of v0.4.0 it's the upper end of that type's age-driven size range instead. */
    public double scaleFor(EntityType type) {
        return plugin.getConfig().getDouble("overrides." + type.name().toLowerCase(), defaultScale());
    }

    // ---------------------------------------------------------------
    // Age-scaling (v0.4.0) -- every natural, non-excluded spawn rolls an age in
    // [minAge(), maxAge()], and its scale is linearly interpolated between minScale() (at
    // minAge()) and scaleFor(type) (at maxAge()).
    // ---------------------------------------------------------------

    public boolean ageScalingEnabled() {
        return plugin.getConfig().getBoolean("age-scaling.enabled", true);
    }

    public int minAge() {
        return Math.max(0, plugin.getConfig().getInt("age-scaling.min-age", 1));
    }

    public int maxAge() {
        return Math.max(minAge(), plugin.getConfig().getInt("age-scaling.max-age", 100));
    }

    /** The scale used at minAge() -- flat across every mob type on purpose (see config.yml). */
    public double minScale() {
        return Math.max(0.0625, plugin.getConfig().getDouble("age-scaling.min-scale", 0.3));
    }

    /** Linearly interpolates {@code type}'s scale for a given age between minScale() (at
     * minAge()) and scaleFor(type) (at maxAge()). Ages outside [minAge(), maxAge()] are clamped
     * first, so a caller never needs to validate age itself. */
    public double scaleForAge(EntityType type, int age) {
        int min = minAge();
        int max = maxAge();
        int clamped = Math.max(min, Math.min(max, age));
        double maxScale = scaleFor(type);
        double minScale = minScale();
        if (max == min) {
            return maxScale;
        }
        double fraction = (clamped - min) / (double) (max - min);
        return minScale + (maxScale - minScale) * fraction;
    }

    /** The inverse of scaleForAge(): given a scale a mob was actually spawned at (e.g. via the
     * raw-scale test-spawn command), works out which age would have produced that same scale, so
     * even an exact/manual scale still gets a coherent, matching age on its name tag. Degenerates
     * to maxAge() if this type's whole range collapses to a single scale. */
    public int ageForScale(EntityType type, double scale) {
        int min = minAge();
        int max = maxAge();
        double maxScale = scaleFor(type);
        double minScale = minScale();
        if (maxScale <= minScale) {
            return max;
        }
        double fraction = clamp01((scale - minScale) / (maxScale - minScale));
        return (int) Math.round(min + fraction * (max - min));
    }

    // ---------------------------------------------------------------
    // Per-dimension spawn rarity control (unchanged since v0.3.3)
    // ---------------------------------------------------------------

    private String worldKey(World.Environment environment) {
        switch (environment) {
            case NETHER:
                return "nether";
            case THE_END:
                return "end";
            default:
                return "overworld";
        }
    }

    public boolean spawnControlEnabled(World.Environment environment) {
        return plugin.getConfig().getBoolean("worlds." + worldKey(environment) + ".spawn-control.enabled", true);
    }

    public double spawnChance(World.Environment environment) {
        return clamp01(plugin.getConfig().getDouble("worlds." + worldKey(environment) + ".spawn-control.spawn-chance", 0.35));
    }

    public boolean spawnLockEnabled() {
        return plugin.getConfig().getBoolean("spawn-lock.enabled", true);
    }

    public void setSpawnLockEnabled(boolean enabled) {
        plugin.getConfig().set("spawn-lock.enabled", enabled);
        plugin.saveConfig();
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    // ---------------------------------------------------------------
    // Stat-scaling multipliers, loot/xp, riding (unchanged since v0.3.3)
    // ---------------------------------------------------------------

    public double healthScaleMultiplier() {
        return plugin.getConfig().getDouble("health-scale-multiplier", 1.0);
    }

    public double damageScaleMultiplier() {
        return plugin.getConfig().getDouble("damage-scale-multiplier", 0.6);
    }

    public double knockbackResistancePerScale() {
        return plugin.getConfig().getDouble("knockback-resistance-per-scale", 0.15);
    }

    public double movementSpeedMultiplier() {
        return plugin.getConfig().getDouble("movement-speed-multiplier", 0.85);
    }

    public boolean lootScaleEnabled() {
        return plugin.getConfig().getBoolean("loot-scale-enabled", true);
    }

    public double lootScaleMultiplier() {
        return plugin.getConfig().getDouble("loot-scale-multiplier", 1.0);
    }

    public double xpScaleMultiplier() {
        return plugin.getConfig().getDouble("xp-scale-multiplier", 1.0);
    }

    public boolean ridingEnabled() {
        return plugin.getConfig().getBoolean("riding.enabled", true);
    }

    public double minimumScaleToRide() {
        return plugin.getConfig().getDouble("riding.minimum-scale-to-ride", 2.0);
    }

    public void reload() {
        plugin.reloadConfig();
        loadExcluded();
        loadRobotTypes();
    }
}
