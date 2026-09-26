package com.nexusuniverse.giants;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Repeating scan: for every landed, unclaimed Miniature Crystal, look for the nearest eligible
 * LivingEntity close enough to "claim" it -- ANY mob is fair game by default, hostile, passive, or
 * boss-tier alike (this project's own age-scaling exclusion list, e.g. Ender Dragon/Wither/Warden,
 * is about natural-spawn sizing and deliberately does NOT apply here; miniature.mechanics.
 * excluded-entity-types is this feature's own, separate, empty-by-default list). Same shape as
 * NexusWarbeasts' MobPickupTask, just shrinking+permanently-taming instead of neutralizing.
 */
public final class MiniatureTameTask extends BukkitRunnable {

    private final JavaPlugin plugin;
    private final MiniatureCrystalTracker tracker;
    private final MiniatureRegistry registry;
    private final GiantMobManager mobManager;
    private final GiantsConfig config;

    private double pickupRadius = 1.5;
    private long unclaimedDespawnMillis = 300_000L;
    private Set<String> excludedTypeNames = new HashSet<>();
    private String tamedMessage = "&dYour Miniature Crystal shrank and tamed &f{mob}&d -- it'll stay this size and follow you forever.";

    public MiniatureTameTask(JavaPlugin plugin, MiniatureCrystalTracker tracker, MiniatureRegistry registry,
                              GiantMobManager mobManager, GiantsConfig config) {
        this.plugin = plugin;
        this.tracker = tracker;
        this.registry = registry;
        this.mobManager = mobManager;
        this.config = config;
    }

    public void loadFromConfig() {
        pickupRadius = plugin.getConfig().getDouble("miniature.mechanics.pickup-radius", 1.5);
        unclaimedDespawnMillis = plugin.getConfig().getLong("miniature.mechanics.unclaimed-despawn-seconds", 300) * 1000L;
        excludedTypeNames = new HashSet<>(plugin.getConfig().getStringList("miniature.mechanics.excluded-entity-types"));
        tamedMessage = plugin.getConfig().getString("miniature.messages.tamed",
                "&dYour Miniature Crystal shrank and tamed &f{mob}&d -- it'll stay this size and follow you forever.");
    }

    @Override
    public void run() {
        if (tracker.all().isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        List<UUID> toForget = new ArrayList<>();

        for (Map.Entry<UUID, MiniatureCrystalTracker.Pending> entry : tracker.all().entrySet()) {
            UUID itemEntityId = entry.getKey();
            MiniatureCrystalTracker.Pending pending = entry.getValue();

            Entity itemEntity = Bukkit.getEntity(itemEntityId);
            if (!(itemEntity instanceof Item)) {
                // claimed by nothing we tracked, or the chunk unloaded and dropped it -- forget it
                toForget.add(itemEntityId);
                continue;
            }

            if (now - pending.droppedAtMillis > unclaimedDespawnMillis) {
                itemEntity.remove();
                toForget.add(itemEntityId);
                continue;
            }

            // Keep resetting the vanilla despawn clock -- our own timeout above is authoritative.
            itemEntity.setTicksLived(1);

            LivingEntity nearest = findNearestEligible(itemEntity.getLocation());
            if (nearest == null) {
                continue;
            }

            tame(nearest, pending.throwerId);
            itemEntity.remove();
            toForget.add(itemEntityId);
        }

        for (UUID id : toForget) {
            tracker.untrack(id);
        }
    }

    private LivingEntity findNearestEligible(Location location) {
        if (location.getWorld() == null) {
            return null;
        }
        LivingEntity nearest = null;
        double nearestDistanceSquared = pickupRadius * pickupRadius;
        for (Entity entity : location.getWorld().getNearbyEntities(location, pickupRadius, pickupRadius, pickupRadius)) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (living instanceof Player) {
                continue; // never a player, obviously
            }
            if (living instanceof ArmorStand) {
                continue; // never one of this plugin's own robot-skin decoys
            }
            if (excludedTypeNames.contains(entityTypeName(living))) {
                continue;
            }
            double distanceSquared = entity.getLocation().distanceSquared(location);
            if (distanceSquared <= nearestDistanceSquared) {
                nearest = living;
                nearestDistanceSquared = distanceSquared;
            }
        }
        return nearest;
    }

    private String entityTypeName(Entity entity) {
        EntityType type = entity.getType();
        return type != null ? type.name() : "";
    }

    private void tame(LivingEntity target, UUID throwerId) {
        EntityType type = target.getType();
        // The exact same "tiniest possible" floor a natural age-1 spawn gets -- reusing this
        // instead of a separate hardcoded scale keeps exactly one definition of "as small as this
        // plugin ever makes anything" in the whole codebase.
        mobManager.applyAgedScale(target, type, config.minAge());
        registry.tame(target, throwerId);

        Player thrower = Bukkit.getPlayer(throwerId);
        if (thrower != null) {
            String mobName = type.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
            thrower.sendMessage(ChatColor.translateAlternateColorCodes('&', tamedMessage.replace("{mob}", mobName)));
        }
    }
}
