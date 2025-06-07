package com.example.diamondplugin.command;

import com.example.diamondplugin.DiamondPlugin;
import com.example.diamondplugin.config.TeleportConfig;
import com.example.diamondplugin.teleport.TeleportManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class HomeCommands implements CommandExecutor, TabCompleter {

    private final DiamondPlugin plugin;
    private final TeleportManager teleportManager;
    private final TeleportConfig teleportConfig;

    private final Map<UUID, BukkitTask> pendingTeleports = new HashMap<>();
    private final Map<UUID, Long> homeCooldowns = new HashMap<>(); // Stores time when cooldown expires

    public HomeCommands(DiamondPlugin plugin) {
        this.plugin = plugin;
        this.teleportManager = plugin.getTeleportManager();
        this.teleportConfig = plugin.getTeleportConfig();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        Player player = (Player) sender;

        switch (command.getName().toLowerCase()) {
            case "sethome":
                return handleSetHome(player, args);
            case "home":
                return handleHome(player, args);
            case "delhome":
                return handleDelHome(player, args);
            case "homes":
            case "listhomes":
                return handleListHomes(player);
        }
        return false;
    }

    private boolean handleSetHome(Player player, String[] args) {
        if (!player.hasPermission("diamondplugin.command.sethome")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to set homes.");
            return true;
        }

        String homeName;
        if (args.length == 0) {
            homeName = teleportConfig.getDefaultHomeName();
        } else {
            homeName = args[0];
        }

        if (!teleportConfig.isAllowMultipleHomes() && !homeName.equalsIgnoreCase(teleportConfig.getDefaultHomeName())) {
            player.sendMessage(ChatColor.RED + "Multiple homes are disabled. You can only set a home named '" + teleportConfig.getDefaultHomeName() + "'.");
            return true;
        }

        if (homeName.length() > 50 || !homeName.matches("^[a-zA-Z0-9_-]+$")) {
            player.sendMessage(ChatColor.RED + "Invalid home name. Must be alphanumeric and less than 50 chars.");
            return true;
        }


        List<String> currentHomes = teleportManager.getPlayerHomeNames(player.getUniqueId());
        int maxHomes = teleportManager.getMaxHomes(player);

        if (!currentHomes.contains(homeName.toLowerCase()) && currentHomes.size() >= maxHomes) {
            player.sendMessage(ChatColor.RED + "You have reached your maximum number of homes (" + maxHomes + ").");
            return true;
        }

        teleportManager.setPlayerHome(player.getUniqueId(), homeName, player.getLocation());
        player.sendMessage(ChatColor.GREEN + "Home '" + homeName + "' set successfully!");
        return true;
    }

    private boolean handleHome(Player player, String[] args) {
        if (!player.hasPermission("diamondplugin.command.home")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        String homeName = (args.length == 0) ? teleportConfig.getDefaultHomeName() : args[0];
        Location homeLocation = teleportManager.getPlayerHome(player.getUniqueId(), homeName);

        if (homeLocation == null) {
            player.sendMessage(ChatColor.RED + "Home '" + homeName + "' not found.");
            return true;
        }

        // Cooldown Check
        if (homeCooldowns.containsKey(player.getUniqueId()) && System.currentTimeMillis() < homeCooldowns.get(player.getUniqueId())) {
            if (!player.hasPermission("diamondplugin.teleport.nocooldown")) {
                long remaining = (homeCooldowns.get(player.getUniqueId()) - System.currentTimeMillis()) / 1000;
                player.sendMessage(ChatColor.RED + "You must wait " + remaining + "s before using /home again.");
                return true;
            }
        }

        int delay = teleportConfig.getHomeTeleportDelaySeconds();
        if (delay > 0 && !player.hasPermission("diamondplugin.teleport.nomovepenalty")) {
            if (pendingTeleports.containsKey(player.getUniqueId())) {
                pendingTeleports.get(player.getUniqueId()).cancel();
                player.sendMessage(ChatColor.YELLOW + "Previous teleport cancelled.");
            }
            player.sendMessage(ChatColor.GREEN + "Teleporting in " + delay + " seconds. Don't move!");
            BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                player.teleport(homeLocation);
                player.sendMessage(ChatColor.GREEN + "Teleported to home '" + homeName + "'.");
                pendingTeleports.remove(player.getUniqueId());
                if (teleportConfig.getHomeTeleportCooldownSeconds() > 0 && !player.hasPermission("diamondplugin.teleport.nocooldown")) {
                    homeCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (teleportConfig.getHomeTeleportCooldownSeconds() * 1000L));
                }
            }, delay * 20L);
            pendingTeleports.put(player.getUniqueId(), task);
        } else {
            player.teleport(homeLocation);
            player.sendMessage(ChatColor.GREEN + "Teleported to home '" + homeName + "'.");
            if (teleportConfig.getHomeTeleportCooldownSeconds() > 0 && !player.hasPermission("diamondplugin.teleport.nocooldown")) {
                 homeCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (teleportConfig.getHomeTeleportCooldownSeconds() * 1000L));
            }
        }
        return true;
    }

    private boolean handleDelHome(Player player, String[] args) {
        if (!player.hasPermission("diamondplugin.command.delhome")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to delete homes.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Usage: /delhome <home_name>");
            return true;
        }
        String homeName = args[0];
        if (teleportManager.deletePlayerHome(player.getUniqueId(), homeName)) {
            player.sendMessage(ChatColor.GREEN + "Home '" + homeName + "' deleted successfully.");
        } else {
            player.sendMessage(ChatColor.RED + "Home '" + homeName + "' not found.");
        }
        return true;
    }

    private boolean handleListHomes(Player player) {
        if (!player.hasPermission("diamondplugin.command.listhomes")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to list homes.");
            return true;
        }
        List<String> homeNames = teleportManager.getPlayerHomeNames(player.getUniqueId());
        if (homeNames.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "You have no homes set. Use /sethome [name] to set one.");
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "Your homes:");
        for (String homeName : homeNames) {
            TextComponent homeComponent = new TextComponent("- " + homeName);
            homeComponent.setColor(net.md_5.bungee.api.ChatColor.GRAY);
            homeComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/home " + homeName));
            player.spigot().sendMessage(homeComponent);
        }
        return true;
    }

    // Getter for PlayerMoveListener_Teleport to access pending teleports
    public Map<UUID, BukkitTask> getPendingTeleports() {
        return pendingTeleports;
    }

    public Map<UUID, Long> getHomeCooldowns() { // Added for SpawnCommands
        return homeCooldowns;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player)) return Collections.emptyList();
        Player player = (Player) sender;
        List<String> completions = new ArrayList<>();

        if (command.getName().equalsIgnoreCase("home") || command.getName().equalsIgnoreCase("delhome")) {
            if (args.length == 1) {
                teleportManager.getPlayerHomeNames(player.getUniqueId()).stream()
                        .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                        .forEach(completions::add);
            }
        } else if (command.getName().equalsIgnoreCase("sethome")) {
            if (args.length == 1 && teleportConfig.isAllowMultipleHomes()) {
                 // Suggest existing home names to overwrite, or they can type a new one
                teleportManager.getPlayerHomeNames(player.getUniqueId()).stream()
                        .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                        .forEach(completions::add);
            }
        }
        // No specific completions for /homes
        return completions.stream().sorted().collect(Collectors.toList());
    }

}
