package com.nexusuniverse.giants;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Server;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Gives configured mob types a robotic look: the real mob is hidden (setInvisible) and a rigid,
 * iron-armored, Observer-headed ArmorStand follows it around as a stand-in "skin" -- the real
 * substitute for a resource pack in a sandbox with no way to ship a custom model/texture. An
 * invisible entity's own name tag still renders even though its body doesn't, so the real mob
 * keeps carrying whatever name GiantMobManager already gave it.
 *
 * As of v0.4.0, the ArmorStand is sized to match whatever scale THIS SPECIFIC spawn actually
 * received (its live Attribute.SCALE, already set by GiantMobManager by the time this
 * runs -- see the explicit EventPriority.HIGH below) instead of always using that type's
 * maximum configured scale. Previously every robot-skinned mob's visible body was always full
 * size regardless of whether that particular spawn actually rolled big or small; with continuous
 * per-mob ages now the norm rather than the exception, a robot at age 3 reads as small too.
 */
public final class RobotSkinManager implements Listener {

    private final GiantsConfig config;
    private final Map<UUID, UUID> companions = new HashMap<>();
    private final Random random = new Random();

    public RobotSkinManager(GiantsConfig config) {
        this.config = config;
    }

    /** Runs AFTER GiantMobManager's own onSpawn (default/NORMAL priority) so this mob's real,
     * age-derived Attribute.SCALE and name tag are already in place by the time the robot
     * skin is built from them. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!config.robotsEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        EntityType type = event.getEntityType();
        if (config.isExcluded(type) || !config.isRobotType(type)) {
            return;
        }

        double scale = 1.0;
        AttributeInstance scaleAttribute = entity.getAttribute(Attribute.SCALE);
        if (scaleAttribute != null) {
            scale = scaleAttribute.getValue();
        }
        applyRobotSkin(entity, scale);
    }

    public void applyRobotSkin(LivingEntity entity, double scale) {
        entity.setInvisible(true);
        // If GiantMobManager already tagged this mob ("Zombie (Age 47)"), keep that and just
        // prefix the robot label onto it; otherwise (age-scaling disabled, or an excluded-from-
        // aging edge case) fall back to the old plain species name.
        String existingName = entity.getCustomName();
        String label = existingName != null ? existingName : prettyName(entity.getType());
        entity.setCustomName(config.robotNamePrefix() + label);
        entity.setCustomNameVisible(true);

        if (entity.getLocation().getWorld() == null) {
            return;
        }
        Entity spawned = entity.getLocation().getWorld().spawnEntity(entity.getLocation(), EntityType.ARMOR_STAND);
        if (!(spawned instanceof ArmorStand skin)) {
            return;
        }

        skin.setBasePlate(false);
        skin.setArms(true);
        skin.setGravity(false);
        skin.setInvulnerable(true);
        skin.setCustomNameVisible(false);
        skin.setPersistent(false);

        AttributeInstance skinScale = skin.getAttribute(Attribute.SCALE);
        if (skinScale != null) {
            skinScale.setBaseValue(scale);
        }

        EntityEquipment equipment = skin.getEquipment();
        if (equipment != null) {
            equipment.setHelmet(new ItemStack(Material.OBSERVER));
            equipment.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            equipment.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
            equipment.setBoots(new ItemStack(Material.IRON_BOOTS));
        }

        EulerAngle armPose = new EulerAngle(Math.toRadians(-8.0), 0, 0);
        skin.setRightArmPose(armPose);
        skin.setLeftArmPose(armPose);

        companions.put(entity.getUniqueId(), skin.getUniqueId());
    }

    /** Runs every 20 ticks (see NexusGiantsPlugin): keeps each robot skin's ArmorStand teleported
     * onto its real mob, occasionally puffs a little dust + anvil-step sound for a mechanical feel,
     * and cleans up a companion entry once its real mob is gone. */
    public void tickAll(Server server) {
        companions.entrySet().removeIf(entry -> {
            Entity realEntity = server.getEntity(entry.getKey());
            Entity skinEntity = server.getEntity(entry.getValue());

            if (!(realEntity instanceof LivingEntity real) || real.isDead() || !real.isValid()) {
                if (skinEntity != null) {
                    skinEntity.remove();
                }
                return true;
            }
            if (!(skinEntity instanceof ArmorStand skin)) {
                return true;
            }

            var location = real.getLocation();
            skin.teleport(location);

            if (random.nextInt(3) == 0) {
                real.getWorld().spawnParticle(Particle.DUST, location.clone().add(0, real.getHeight() / 2.0, 0),
                        4, 0.3, 0.3, 0.3, new Particle.DustOptions(Color.fromRGB(180, 40, 40), 1f));
                real.getWorld().playSound(location, org.bukkit.Sound.BLOCK_ANVIL_STEP, 0.4f, 1.4f);
            }
            return false;
        });
    }

    private String prettyName(EntityType type) {
        String lower = type.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
