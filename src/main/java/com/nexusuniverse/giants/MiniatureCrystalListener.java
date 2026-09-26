package com.nexusuniverse.giants;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Throwing the Miniature Crystal (right-click) and turning a landed throw into a claimable dropped
 * item -- same shape as NexusWarbeasts' CrystalListener, just for a different item/plugin.
 */
public final class MiniatureCrystalListener implements Listener {

    private final MiniatureCrystalItem crystalItem;
    private final MiniatureCrystalTracker tracker;
    private final NamespacedKey throwMarkerKey;
    private final NamespacedKey throwerKey;

    public MiniatureCrystalListener(JavaPlugin plugin, MiniatureCrystalItem crystalItem, MiniatureCrystalTracker tracker) {
        this.crystalItem = crystalItem;
        this.tracker = tracker;
        this.throwMarkerKey = new NamespacedKey(plugin, "miniature_crystal_throw");
        this.throwerKey = new NamespacedKey(plugin, "miniature_thrower");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // only handle the main-hand event once, not also the off-hand copy
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (!crystalItem.isMiniatureCrystal(inHand)) {
            return;
        }

        event.setCancelled(true);

        if (player.getGameMode() != GameMode.CREATIVE) {
            inHand.setAmount(inHand.getAmount() - 1);
        }

        Snowball projectile = player.launchProjectile(Snowball.class);
        projectile.getPersistentDataContainer().set(throwMarkerKey, PersistentDataType.BYTE, (byte) 1);
        projectile.getPersistentDataContainer().set(throwerKey, PersistentDataType.STRING, player.getUniqueId().toString());
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Entity hitEntity = event.getEntity();
        if (!(hitEntity instanceof Projectile projectile)) {
            return; // defensive -- ProjectileHitEvent's entity is always a Projectile in practice
        }
        Byte marker = projectile.getPersistentDataContainer().get(throwMarkerKey, PersistentDataType.BYTE);
        if (marker == null || marker != (byte) 1) {
            return;
        }

        String throwerRaw = projectile.getPersistentDataContainer().get(throwerKey, PersistentDataType.STRING);
        projectile.remove();
        if (throwerRaw == null) {
            return; // shouldn't happen, but don't drop an unclaimable, untracked crystal
        }

        Location dropLocation = projectile.getLocation();
        Item dropped = dropLocation.getWorld() != null
                ? dropLocation.getWorld().dropItem(dropLocation, crystalItem.create(1))
                : null;
        if (dropped == null) {
            return;
        }

        // Blocks both players AND vanilla mob pickup AI -- our own MiniatureTameTask is what
        // actually "claims" this for a mob, on our own terms and radius.
        dropped.setPickupDelay(Integer.MAX_VALUE);
        dropped.getPersistentDataContainer().set(throwerKey, PersistentDataType.STRING, throwerRaw);

        tracker.track(dropped.getUniqueId(), java.util.UUID.fromString(throwerRaw));
    }
}
