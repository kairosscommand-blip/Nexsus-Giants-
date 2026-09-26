package com.nexusuniverse.giants;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks every landed (dropped-on-the-ground) Miniature Crystal that's still waiting for a mob to
 * wander close enough to claim it, keyed by the dropped Item entity's UUID -- identical shape to
 * NexusWarbeasts' PendingCrystalTracker, kept as its own small class so MiniatureTameTask only
 * ever has to check these specific locations, not every item entity in every loaded chunk.
 */
public final class MiniatureCrystalTracker {

    public static final class Pending {
        public final UUID throwerId;
        public final long droppedAtMillis;

        public Pending(UUID throwerId, long droppedAtMillis) {
            this.throwerId = throwerId;
            this.droppedAtMillis = droppedAtMillis;
        }
    }

    private final Map<UUID, Pending> pendingByItemEntity = new ConcurrentHashMap<>();

    public void track(UUID itemEntityId, UUID throwerId) {
        pendingByItemEntity.put(itemEntityId, new Pending(throwerId, System.currentTimeMillis()));
    }

    public Pending get(UUID itemEntityId) {
        return pendingByItemEntity.get(itemEntityId);
    }

    public void untrack(UUID itemEntityId) {
        pendingByItemEntity.remove(itemEntityId);
    }

    public Map<UUID, Pending> all() {
        return pendingByItemEntity;
    }
}
