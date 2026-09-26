package com.nexusuniverse.giants;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public final class GiantsCommand implements CommandExecutor {

    private static final String USAGE = "§cUsage: /nexusgiants <reload|spawn <entitytype> [age]"
            + "|spawnscale <entitytype> <scale>|spawnlock <on|off|status>>";

    private final GiantsConfig config;
    private final GiantMobManager mobManager;

    public GiantsCommand(GiantsConfig config, GiantMobManager mobManager) {
        this.config = config;
        this.mobManager = mobManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(USAGE);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                handleReload(sender);
                break;
            case "spawn":
                handleSpawn(sender, args);
                break;
            case "spawnscale":
                handleSpawnScale(sender, args);
                break;
            case "spawnlock":
                handleSpawnLock(sender, args);
                break;
            default:
                sender.sendMessage(USAGE);
                break;
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("nexusgiants.admin")) {
            sender.sendMessage("§cNo permission.");
            return;
        }
        config.reload();
        sender.sendMessage("§aConfig reloaded.");
    }

    /** /nexusgiants spawn <entitytype> [age] -- age-driven test spawn, matching how a natural
     * spawn now works: no age given rolls a random one same as a real natural spawn would. */
    private void handleSpawn(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nexusgiants.admin")) {
            sender.sendMessage("§cNo permission.");
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("§cUsage: /nexusgiants spawn <entitytype> [age]");
            return;
        }

        EntityType type;
        try {
            type = EntityType.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException ex) {
            player.sendMessage("§cUnknown entity type.");
            return;
        }

        Entity spawned = player.getWorld().spawnEntity(player.getLocation(), type);
        if (!(spawned instanceof LivingEntity entity)) {
            player.sendMessage("§cThat entity type can't be scaled.");
            spawned.remove();
            return;
        }

        if (args.length >= 3) {
            int age;
            try {
                age = Integer.parseInt(args[2]);
            } catch (NumberFormatException ex) {
                player.sendMessage("§cAge must be a whole number -- spawning at a random age instead.");
                mobManager.applyGiant(entity, type);
                player.sendMessage("Spawned " + type.name() + ".");
                return;
            }
            mobManager.applyAgedScale(entity, type, age);
            player.sendMessage("Spawned " + type.name() + " at age " + age + ".");
            return;
        }

        mobManager.applyGiant(entity, type);
        player.sendMessage("Spawned " + type.name() + ".");
    }

    /** /nexusgiants spawnscale <entitytype> <scale> -- the old v0.3.3 exact-scale test spawn,
     * kept for power users who want a precise scale rather than an age. Still gets a coherent,
     * matching age on its name tag via GiantsConfig.ageForScale(). */
    private void handleSpawnScale(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nexusgiants.admin")) {
            sender.sendMessage("§cNo permission.");
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return;
        }
        if (args.length < 3) {
            player.sendMessage("§cUsage: /nexusgiants spawnscale <entitytype> <scale>");
            return;
        }

        EntityType type;
        try {
            type = EntityType.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException ex) {
            player.sendMessage("§cUnknown entity type.");
            return;
        }

        Entity spawned = player.getWorld().spawnEntity(player.getLocation(), type);
        if (!(spawned instanceof LivingEntity entity)) {
            player.sendMessage("§cThat entity type can't be scaled.");
            spawned.remove();
            return;
        }

        double scale;
        try {
            scale = Double.parseDouble(args[2]);
        } catch (NumberFormatException ex) {
            player.sendMessage("§cScale must be a number -- spawning at a random age instead.");
            mobManager.applyGiant(entity, type);
            player.sendMessage("Spawned " + type.name() + ".");
            return;
        }

        mobManager.applyAgedScaleFromRawScale(entity, type, scale);
        player.sendMessage("Spawned " + type.name() + " at scale " + scale + ".");
    }

    private void handleSpawnLock(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nexusgiants.admin")) {
            sender.sendMessage("§cNo permission.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /nexusgiants spawnlock <on|off|status>");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "on":
                config.setSpawnLockEnabled(true);
                sender.sendMessage("§aSpawn lock ON -- doMobSpawning will be forced back on if anyone changes it, including you.");
                break;
            case "off":
                config.setSpawnLockEnabled(false);
                sender.sendMessage("§eSpawn lock OFF -- doMobSpawning can be changed normally now.");
                break;
            case "status":
                sender.sendMessage("Spawn lock: " + (config.spawnLockEnabled() ? "ON" : "OFF"));
                break;
            default:
                sender.sendMessage("§cUsage: /nexusgiants spawnlock <on|off|status>");
                break;
        }
    }
}
