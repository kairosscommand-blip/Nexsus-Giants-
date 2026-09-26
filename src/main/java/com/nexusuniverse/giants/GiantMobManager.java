package com.nexusuniverse.giants;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Random;

/**
 * The core of NexusGiants' sizing: every NATURAL, non-excluded spawn rolls a random AGE (see
 * GiantsConfig.minAge()/maxAge()), derives that mob's scale from its age (the older, the bigger --
 * GiantsConfig.scaleForAge()), applies it via the real Attribute.SCALE hitbox resize (not
 * a visual trick), scales health/damage/knockback-resistance/movement-speed proportionally, and
 * gives the mob a floating name tag showing its species and exact age.
 *
 * As of v0.4.0 this replaced the old flat "giant-chance" (a rare all-or-nothing roll to become one
 * fixed giant size, everything else staying vanilla-normal) with a full spread across every
 * natural spawn -- see CHANGES.md for why.
 */
public final class GiantMobManager implements Listener {

    /** Namespaced key the rolled/assigned age is stored under in each mob's own
     * PersistentDataContainer -- survives chunk unload/reload, and lets any future feature (or
     * another plugin) read a mob's age back out without re-deriving it from its current scale. */
    public static final String AGE_KEY = "age";

    private final GiantsConfig config;
    private final NamespacedKey ageKey;
    private final Random random = new Random();

    public GiantMobManager(GiantsConfig config, Plugin plugin) {
        this.config = config;
        this.ageKey = new NamespacedKey(plugin, AGE_KEY);
    }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }

        boolean natural = event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL;
        World.Environment environment = entity.getWorld().getEnvironment();

        // Per-dimension rarity control -- unchanged since v0.3.3, and independent of everything
        // below: this decides whether a natural spawn happens at all, not how big it ends up.
        if (natural && config.spawnControlEnabled(environment)) {
            if (random.nextDouble() >= config.spawnChance(environment)) {
                event.setCancelled(true);
                return;
            }
        }

        EntityType type = event.getEntityType();
        if (config.isExcluded(type)) {
            return;
        }

        // Only natural spawns get an age/size rolled onto them -- spawner/egg/breeding/
        // command-summoned mobs stay exactly as vanilla spawns them, same scope this plugin has
        // always kept (see config.yml's age-scaling comment). An admin test-spawn goes through
        // GiantsCommand -> applyGiant()/applyAgedScale() instead, not this handler.
        if (!natural || !config.ageScalingEnabled()) {
            return;
        }

        int age = rollAge();
        applyAgedScale(entity, type, age);
    }

    /** Rolls a uniformly random age in [minAge(), maxAge()] -- used both for natural spawns above
     * and for GiantsCommand's "/nexusgiants spawn &lt;type&gt;" with no explicit age given. */
    public int rollAge() {
        int min = config.minAge();
        int max = config.maxAge();
        return min + random.nextInt(max - min + 1);
    }

    /** Applies {@code age}'s derived scale to {@code entity} (clamped to the configured range),
     * plus the proportional stat scaling and the "<Species> (Age N)" name tag. This is the single
     * entry point every age-driven spawn (natural or admin test-spawn) goes through. */
    public void applyAgedScale(LivingEntity entity, EntityType type, int age) {
        int min = config.minAge();
        int max = config.maxAge();
        int clampedAge = Math.max(min, Math.min(max, age));
        double scale = config.scaleForAge(type, clampedAge);
        applyGiantWithScale(entity, scale);
        tagWithAge(entity, type, clampedAge);
    }

    /** For the raw-scale test-spawn path ("/nexusgiants spawnscale") -- applies an EXACT scale
     * (not derived from a rolled age), then works out which age would have produced that same
     * scale purely so the name tag still reads a coherent, matching age. */
    public void applyAgedScaleFromRawScale(LivingEntity entity, EntityType type, double scale) {
        applyGiantWithScale(entity, scale);
        tagWithAge(entity, type, config.ageForScale(type, scale));
    }

    /** Legacy-shaped convenience used when no explicit age/scale was given at all (e.g. plain
     * "/nexusgiants spawn &lt;type&gt;") -- rolls a random age exactly like a natural spawn would. */
    public void applyGiant(LivingEntity entity, EntityType type) {
        if (config.isExcluded(type)) {
            return;
        }
        applyAgedScale(entity, type, rollAge());
    }

    private void tagWithAge(LivingEntity entity, EntityType type, int age) {
        entity.getPersistentDataContainer().set(ageKey, PersistentDataType.INTEGER, age);
        entity.setCustomName(prettyName(type) + " (Age " + age + ")");
        entity.setCustomNameVisible(true);
    }

    /** Reads back the age previously stored by tagWithAge(), or -1 if this entity was never
     * aged/scaled by this plugin (excluded type, non-natural spawn, or age-scaling disabled at the
     * time it spawned). */
    public int ageOf(LivingEntity entity) {
        Integer age = entity.getPersistentDataContainer().get(ageKey, PersistentDataType.INTEGER);
        return age == null ? -1 : age;
    }

    private static String prettyName(EntityType type) {
        String lower = type.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    /** Applies the real Attribute.SCALE hitbox resize plus proportional health/damage/
     * knockback-resistance/movement-speed scaling for a given exact scale. Pure stat application --
     * naming/PDC tagging happens in the callers above, since GiantsCommand's raw-scale path also
     * needs this without going through the age machinery at all. */
    public void applyGiantWithScale(LivingEntity entity, double scale) {
        setAttribute(entity, Attribute.SCALE, scale);

        double healthMultiplier = 1.0 + (scale - 1.0) * config.healthScaleMultiplier();
        scaleAttribute(entity, Attribute.MAX_HEALTH, healthMultiplier);
        AttributeInstance maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            entity.setHealth(maxHealth.getValue());
        }

        double damageMultiplier = 1.0 + (scale - 1.0) * config.damageScaleMultiplier();
        scaleAttribute(entity, Attribute.ATTACK_DAMAGE, damageMultiplier);

        AttributeInstance knockback = entity.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (knockback != null) {
            double added = Math.min(1.0, (scale - 1.0) * config.knockbackResistancePerScale());
            knockback.setBaseValue(Math.min(1.0, knockback.getBaseValue() + added));
        }

        double speedMultiplier = config.movementSpeedMultiplier();
        if (speedMultiplier != 1.0) {
            scaleAttribute(entity, Attribute.MOVEMENT_SPEED, speedMultiplier);
        }
    }

    private void setAttribute(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private void scaleAttribute(LivingEntity entity, Attribute attribute, double multiplier) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(instance.getBaseValue() * multiplier);
        }
    }
}
