package com.example.diamondplugin.listener;

import com.example.diamondplugin.DiamondPlugin;
import com.example.diamondplugin.claim.Claim;
import com.example.diamondplugin.claim.ClaimManager;
import com.example.diamondplugin.config.ClaimConfig; // Added
import org.bukkit.Bukkit; // Added for OfflinePlayer
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class ClaimProtectionListener implements Listener {

    private final DiamondPlugin plugin;
    private final ClaimManager claimManager;
    private final ClaimConfig claimConfig; // Added

    public ClaimProtectionListener(DiamondPlugin plugin) {
        this.plugin = plugin;
        this.claimManager = plugin.getClaimManager();
        this.claimConfig = plugin.getClaimConfig(); // Added
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlock().getLocation();

        if (player.hasPermission("diamondplugin.admin.bypassclaims")) {
            return; // Admin bypass
        }

        Claim claim = claimManager.getClaimAt(location);
        if (claim != null) {
            if (!claim.getOwnerUUID().equals(player.getUniqueId()) && !claim.isTrusted(player.getUniqueId())) {
                event.setCancelled(true);
                String ownerName = Bukkit.getOfflinePlayer(claim.getOwnerUUID()).getName();
                if (ownerName == null) ownerName = "unknown";
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    claimConfig.getMsgCannotBreak().replace("{owner}", ownerName)));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlock().getLocation();

        if (player.hasPermission("diamondplugin.admin.bypassclaims")) {
            return; // Admin bypass
        }

        Claim claim = claimManager.getClaimAt(location);
        if (claim != null) {
            if (!claim.getOwnerUUID().equals(player.getUniqueId()) && !claim.isTrusted(player.getUniqueId())) {
                event.setCancelled(true);
                String ownerName = Bukkit.getOfflinePlayer(claim.getOwnerUUID()).getName();
                if (ownerName == null) ownerName = "unknown";
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    claimConfig.getMsgCannotPlace().replace("{owner}", ownerName)));
            }
        }
    }
}
