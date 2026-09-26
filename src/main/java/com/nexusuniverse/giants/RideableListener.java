package com.nexusuniverse.giants;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

/**
 * Right-click a scaled-up mob to mount it -- a passenger ride, not steered: the mob's own AI still
 * drives where it goes, same as being a passenger on any vanilla mount that isn't a horse/pig/
 * strider. Unchanged since v0.3.3 -- works automatically with v0.4.0's continuous ages since it
 * already just reads the entity's live scale, whatever produced it.
 */
public final class RideableListener implements Listener {

    private final GiantsConfig config;

    public RideableListener(GiantsConfig config) {
        this.config = config;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!config.ridingEnabled()) {
            return;
        }
        if (!(event.getRightClicked() instanceof LivingEntity entity)) {
            return;
        }
        Player player = event.getPlayer();
        if (entity.getPassengers().contains(player)) {
            return;
        }
        if (entity.isInvisible()) {
            // A robot-skinned mob's real body is invisible -- riding the hidden real entity would
            // look broken (the player would appear mounted on thin air next to its ArmorStand
            // skin), so it's skipped entirely rather than half-supported.
            return;
        }
        AttributeInstance scaleAttribute = entity.getAttribute(Attribute.SCALE);
        if (scaleAttribute == null) {
            return;
        }
        if (scaleAttribute.getValue() < config.minimumScaleToRide()) {
            return;
        }
        event.setCancelled(true);
        entity.addPassenger(player);
    }
}
