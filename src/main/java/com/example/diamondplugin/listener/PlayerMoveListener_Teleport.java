package com.example.diamondplugin.listener;

import com.example.diamondplugin.DiamondPlugin;
import com.example.diamondplugin.command.HomeCommands;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;

public class PlayerMoveListener_Teleport implements Listener {

    private final DiamondPlugin plugin;
    // Need a way to access pendingTeleports from HomeCommands
    // This could be done by passing HomeCommands instance, or making pendingTeleports accessible via DiamondPlugin
    // This will require HomeCommands to have a public getter for its pendingTeleports map.

    public PlayerMoveListener_Teleport(DiamondPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        HomeCommands homeCommands = plugin.getHomeCommandsExecutor();

        if (homeCommands != null) {
            Map<UUID, BukkitTask> pendingTeleports = homeCommands.getPendingTeleports();
            if (pendingTeleports.containsKey(playerUuid)) {
                Location from = event.getFrom();
                Location to = event.getTo();

                // Check if the player has moved more than just looking around
                if (to == null || from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                     if (!player.hasPermission("diamondplugin.teleport.nomovepenalty")) {
                        BukkitTask task = pendingTeleports.remove(playerUuid);
                        if (task != null) {
                            task.cancel();
                            player.sendMessage(ChatColor.RED + "Teleport cancelled because you moved.");
                        }
                    }
                }
            }
        }
    }
}
