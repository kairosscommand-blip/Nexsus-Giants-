package com.nexusuniverse.giants;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class GiantsCommand implements CommandExecutor {

    private static final String USAGE = "§cUsage: /nexusgiants <reload|spawn <entitytype> [age]"
            + "|spawnscale <entitytype> <scale>|spawnlock <on|off|status>"
            + "|minigive <player> [amount]|minirelease|minilist [player]>";

    private final GiantsConfig config;
    private final GiantMobManager mobManager;
    private final MiniatureCrystalItem miniatureCrystalItem;
    private final MiniatureRegistry miniatureRegistry;

    public GiantsCommand(GiantsConfig config, GiantMobManager mobManager,
                          MiniatureCrystalItem miniatureCrystalItem, MiniatureRegistry miniatureRegistry) {
        this.config = config;
        this.mobManager = mobManager;
        this.miniatureCrystalItem = miniatureCrystalItem;
        this.miniatureRegistry = miniatureRegistry;
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
            case "minigive":
                handleMiniGive(sender, args);
                break;
            case "minirelease":
                handleMiniRelease(sender);
                break;
            case "minilist":
                handleMiniList(sender, args);
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

    /** /nexusgiants minigive <player> [amount] -- mints Miniature Crystal(s) into a player's
     * inventory. Throwing one at any mob shrinks it to the smallest size this plugin ever makes
     * anything and tames it to whoever threw it, permanently -- see MiniatureTameTask. */
    private void handleMiniGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nexusgiants.admin")) {
            sender.sendMessage("§cNo permission.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /nexusgiants minigive <player> [amount]");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cNo online player called \"" + args[1] + "\".");
            return;
        }
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Integer.parseInt(args[2]));
            } catch (NumberFormatException ex) {
                sender.sendMessage("§cThat's not a number: \"" + args[2] + "\".");
                return;
            }
        }
        ItemStack crystals = miniatureCrystalItem.create(amount);
        target.getInventory().addItem(crystals);
        target.sendMessage("§dYou received " + amount + " Miniature Crystal(s).");
        sender.sendMessage("§aGave " + target.getName() + " " + amount + " Miniature Crystal(s).");
    }

    /** /nexusgiants minirelease -- un-tames whichever tamed miniature the player is looking at.
     * Leaves its size alone (still tiny); it just stops being anyone's pet and can despawn again
     * like an ordinary wild mob. */
    private void handleMiniRelease(CommandSender sender) {
        if (!sender.hasPermission("nexusgiants.admin")) {
            sender.sendMessage("§cNo permission.");
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this.");
            return;
        }
        Entity target = player.getTargetEntity(10);
        if (!(target instanceof LivingEntity living) || !miniatureRegistry.isTamedMiniature(living)) {
            sender.sendMessage("§cNo tamed miniature in your line of sight.");
            return;
        }
        miniatureRegistry.release(living);
        sender.sendMessage("§aReleased that miniature -- it's nobody's pet anymore (still tiny, but can despawn normally now).");
    }

    /** /nexusgiants minilist [player] -- counts tamed miniatures owned by a player, across every
     * currently LOADED chunk in every world (deliberately never force-loads chunks just to count
     * pets, same discipline as the rest of this project -- one sitting in an unloaded area of the
     * map just won't be counted until that area is loaded again). */
    private void handleMiniList(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nexusgiants.admin")) {
            sender.sendMessage("§cNo permission.");
            return;
        }
        UUID targetId;
        String targetName;
        if (args.length >= 2) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(args[1]);
            targetId = offline.getUniqueId();
            targetName = args[1];
        } else if (sender instanceof Player player) {
            targetId = player.getUniqueId();
            targetName = player.getName();
        } else {
            sender.sendMessage("§cUsage: /nexusgiants minilist <player> (required from console).");
            return;
        }

        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof LivingEntity living
                        && miniatureRegistry.isTamedMiniature(living)
                        && miniatureRegistry.isOwnedBy(living, targetId)) {
                    count++;
                }
            }
        }
        sender.sendMessage("§6" + targetName + " §6has §f" + count + " §6tamed miniature(s) in currently loaded chunks.");
    }
}
