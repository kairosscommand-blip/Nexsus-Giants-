package com.nexusuniverse.giants;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Locale;

/**
 * Cancels "/gamerule doMobSpawning &lt;value&gt;" from players and console before it runs, while
 * spawn-lock is on -- the "prevention" half (SpawnLockManager is the "correction" half: a periodic
 * re-assertion for the cases this can't intercept, like a command block). Unchanged since v0.3.3.
 */
public final class SpawnLockGuard implements Listener {

    private final GiantsConfig config;

    public SpawnLockGuard(GiantsConfig config) {
        this.config = config;
    }

    @EventHandler
    public void onConsoleCommand(ServerCommandEvent event) {
        if (!config.spawnLockEnabled()) {
            return;
        }
        if (!isLockedCommand(event.getCommand())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!config.spawnLockEnabled()) {
            return;
        }
        String message = event.getMessage();
        String command = message.startsWith("/") ? message.substring(1) : message;
        if (!isLockedCommand(command)) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage("§cMob spawning is locked (/nexusgiants spawnlock is ON) -- "
                + "that command won't run until it's switched off.");
    }

    /** True only for "gamerule domobspawning &lt;something&gt;" -- a bare "/gamerule domobspawning"
     * with no value (just querying the current value) is left alone, same as v0.3.3. */
    private boolean isLockedCommand(String rawCommand) {
        if (rawCommand == null || rawCommand.isBlank()) {
            return false;
        }
        String trimmed = rawCommand.trim();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        String[] parts = trimmed.trim().split("\\s+");
        if (parts.length < 2) {
            return false;
        }
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        String rule = parts[1].toLowerCase(Locale.ROOT);
        boolean hasValue = parts.length > 2;
        return cmd.equals("gamerule") && rule.equals("domobspawning") && hasValue;
    }
}
