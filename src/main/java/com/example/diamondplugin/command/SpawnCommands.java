package com.example.diamondplugin.command;

import com.example.diamondplugin.DiamondPlugin;
import com.example.diamondplugin.config.TeleportConfig;
import com.example.diamondplugin.teleport.TeleportManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SpawnCommands implements CommandExecutor {

    private final DiamondPlugin plugin;
    private final TeleportManager teleportManager;
    private final TeleportConfig teleportConfig;

    // Using HomeCommands' pendingTeleports and cooldowns for now.
    // This requires HomeCommands instance to be accessible.
    // A more robust solution might involve a shared TeleportCooldownManager or similar.
    private final com.example.diamondplugin.command.HomeCommands homeCommands;


    public SpawnCommands(DiamondPlugin plugin, com.example.diamondplugin.command.HomeCommands homeCommands) {
        this.plugin = plugin;
        this.teleportManager = plugin.getTeleportManager();
        this.teleportConfig = plugin.getTeleportConfig();
        this.homeCommands = homeCommands; // For accessing pendingTeleports and cooldowns
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("setspawn")) {
            return handleSetSpawn(sender, args);
        } else if (command.getName().equalsIgnoreCase("spawn")) {
            return handleSpawn(sender, args);
        }
        return false;
    }

    private boolean handleSetSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        if (!sender.hasPermission("diamondplugin.command.setspawn")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        Player player = (Player) sender;
        teleportManager.setSpawnLocation(player.getLocation());
        player.sendMessage(ChatColor.GREEN + "Server spawn point set to your current location!");
        return true;
    }

    private boolean handleSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players (for now)."); // Console spawn could be added later
            return true;
        }
        Player player = (Player) sender;

        if (!player.hasPermission("diamondplugin.command.spawn")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        Location spawnLocation = teleportManager.getSpawnLocation();
        if (spawnLocation == null) {
            player.sendMessage(ChatColor.RED + "Spawn point is not set! Please contact an administrator.");
            return true;
        }

        // Cooldown Check (using homeCooldowns map for now, might need a separate spawnCooldowns)
        Map<UUID, Long> cooldowns = homeCommands.getHomeCooldowns(); // Assuming a public getter in HomeCommands
        if (cooldowns.containsKey(player.getUniqueId()) && System.currentTimeMillis() < cooldowns.get(player.getUniqueId())) {
            if (!player.hasPermission("diamondplugin.teleport.nocooldown")) { // Or diamondplugin.spawn.nocooldown
                long remaining = (cooldowns.get(player.getUniqueId()) - System.currentTimeMillis()) / 1000;
                player.sendMessage(ChatColor.RED + "You must wait " + remaining + "s before using /spawn again.");
                return true;
            }
        }

        int delay = teleportConfig.getSpawnTeleportDelaySeconds();
        Map<UUID, BukkitTask> pendingTeleports = homeCommands.getPendingTeleports();

        if (delay > 0 && !player.hasPermission("diamondplugin.teleport.nomovepenalty")) { // Or diamondplugin.spawn.nomovepenalty
            if (pendingTeleports.containsKey(player.getUniqueId())) {
                pendingTeleports.get(player.getUniqueId()).cancel();
                player.sendMessage(ChatColor.YELLOW + "Previous teleport cancelled.");
            }
            player.sendMessage(ChatColor.GREEN + "Teleporting to spawn in " + delay + " seconds. Don't move!");
            BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                player.teleport(spawnLocation);
                player.sendMessage(ChatColor.GREEN + "Teleported to spawn.");
                pendingTeleports.remove(player.getUniqueId());
                if (teleportConfig.getSpawnTeleportCooldownSeconds() > 0 && !player.hasPermission("diamondplugin.teleport.nocooldown")) {
                    cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (teleportConfig.getSpawnTeleportCooldownSeconds() * 1000L));
                }
            }, delay * 20L);
            pendingTeleports.put(player.getUniqueId(), task);
        } else {
            player.teleport(spawnLocation);
            player.sendMessage(ChatColor.GREEN + "Teleported to spawn.");
            if (teleportConfig.getSpawnTeleportCooldownSeconds() > 0 && !player.hasPermission("diamondplugin.teleport.nocooldown")) {
                 cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (teleportConfig.getSpawnTeleportCooldownSeconds() * 1000L));
            }
        }
        return true;
    }
}
