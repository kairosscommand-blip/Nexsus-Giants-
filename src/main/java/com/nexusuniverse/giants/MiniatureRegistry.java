package com.nexusuniverse.giants;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Who owns a given tamed miniature, and whether it's one at all. Persisted directly on the tamed
 * entity's own PersistentDataContainer -- survives chunk unload/reload and server restarts
 * automatically as part of the entity's own saved data, no separate file needed, same pattern as
 * NexusWarbeasts' GiverRegistry and NexusHatchlings' owner tag.
 *
 * Ownership is single-owner and always overwritable: throwing a second Miniature Crystal at an
 * already-tamed mini simply re-tames it to the new thrower (last crystal wins). Unlike
 * NexusWarbeasts' shareable "givers" set, this is deliberately closer to a real pet -- each
 * player builds their OWN army, not a jointly-owned one.
 */
public final class MiniatureRegistry {

    private final NamespacedKey ownerKey;
    private final NamespacedKey tamedKey;

    public MiniatureRegistry(JavaPlugin plugin) {
        this.ownerKey = new NamespacedKey(plugin, "miniature_owner");
        this.tamedKey = new NamespacedKey(plugin, "miniature_tamed");
    }

    public boolean isTamedMiniature(LivingEntity entity) {
        Byte marker = entity.getPersistentDataContainer().get(tamedKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    /** Null if never tamed, or if the stored uuid is somehow corrupt. */
    public UUID ownerOf(LivingEntity entity) {
        String raw = entity.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public boolean isOwnedBy(LivingEntity entity, UUID playerId) {
        UUID owner = ownerOf(entity);
        return owner != null && owner.equals(playerId);
    }

    /** Tames (or re-tames, to a new owner) {@code entity} permanently. Caller is responsible for
     * having already shrunk it -- this method only records ownership and makes sure it survives
     * like a real kept pet instead of despawning like an ordinary wild mob. */
    public void tame(LivingEntity entity, UUID owner) {
        entity.getPersistentDataContainer().set(tamedKey, PersistentDataType.BYTE, (byte) 1);
        entity.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.toString());
        entity.setRemoveWhenFarAway(false);
        entity.setPersistent(true);
    }

    /** Un-tames {@code entity} -- clears ownership/protection and lets it despawn normally again
     * like any other wild mob. Deliberately leaves its size alone: releasing doesn't grow it back
     * up, it just stops being anyone's pet. */
    public void release(LivingEntity entity) {
        entity.getPersistentDataContainer().remove(tamedKey);
        entity.getPersistentDataContainer().remove(ownerKey);
        entity.setRemoveWhenFarAway(true);
    }
}
