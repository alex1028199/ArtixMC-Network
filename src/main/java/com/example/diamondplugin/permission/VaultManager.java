package com.example.diamondplugin.permission;

import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import com.example.diamondplugin.DiamondPlugin;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class VaultManager {

    private final DiamondPlugin plugin;
    private Permission perms = null;
    private boolean vaultAvailable = false;

    public VaultManager(DiamondPlugin plugin) {
        this.plugin = plugin;
        setupPermissions();
    }

    private void setupPermissions() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().info("Vault plugin not found. Vault integration disabled.");
            vaultAvailable = false;
            return;
        }
        RegisteredServiceProvider<Permission> rsp = Bukkit.getServer().getServicesManager().getRegistration(Permission.class);
        if (rsp == null) {
            plugin.getLogger().warning("No permission service provider found by Vault. Vault integration disabled.");
            vaultAvailable = false;
            return;
        }
        perms = rsp.getProvider();
        if (perms == null) {
             plugin.getLogger().warning("Vault permission provider is null. Vault integration disabled.");
            vaultAvailable = false;
        } else {
            plugin.getLogger().info("Successfully hooked into Vault for permissions.");
            vaultAvailable = true;
        }
    }

    public boolean isVaultAvailable() {
        return vaultAvailable && perms != null;
    }

    public boolean addPlayerToGroup(Player player, String groupName) {
        if (!isVaultAvailable()) return false;
        try {
            return perms.playerAddGroup(player, groupName);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error adding player " + player.getName() + " to group " + groupName + " via Vault", e);
            return false;
        }
    }

    public boolean addPlayerToGroup(UUID playerUuid, String groupName) {
        if (!isVaultAvailable()) return false;
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUuid);
        // Vault's playerAddGroup typically requires a world name for offline players,
        // but it's often handled by the perm plugin if primary world is configured.
        // For simplicity, we'll rely on that or assume player is online if direct add is needed.
        // A more robust solution might require getting the player's last known world or a default world.
        if (offlinePlayer.isOnline()) {
            return addPlayerToGroup(offlinePlayer.getPlayer(), groupName);
        } else {
             // This might not work for all permissions plugins for offline players without a world context.
            try {
                 return perms.playerAddGroup(null, offlinePlayer, groupName);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error adding offline player " + offlinePlayer.getName() + " to group " + groupName + " via Vault", e);
                return false;
            }
        }
    }


    public boolean removePlayerFromGroup(Player player, String groupName) {
        if (!isVaultAvailable()) return false;
        try {
            return perms.playerRemoveGroup(player, groupName);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error removing player " + player.getName() + " from group " + groupName + " via Vault", e);
            return false;
        }
    }

    public boolean removePlayerFromGroup(UUID playerUuid, String groupName) {
        if (!isVaultAvailable()) return false;
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUuid);
         if (offlinePlayer.isOnline()) {
            return removePlayerFromGroup(offlinePlayer.getPlayer(), groupName);
        } else {
            try {
                return perms.playerRemoveGroup(null, offlinePlayer, groupName);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error removing offline player " + offlinePlayer.getName() + " from group " + groupName + " via Vault", e);
                return false;
            }
        }
    }

    public List<String> getPlayerGroups(Player player) {
        if (!isVaultAvailable()) return Arrays.asList();
        try {
            String[] groups = perms.getPlayerGroups(player);
            return groups == null ? Arrays.asList() : Arrays.asList(groups);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error getting groups for player " + player.getName() + " via Vault", e);
            return Arrays.asList();
        }
    }

    public List<String> getPlayerGroups(UUID playerUuid) {
        if (!isVaultAvailable()) return Arrays.asList();
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUuid);
        if (offlinePlayer.isOnline()) {
            return getPlayerGroups(offlinePlayer.getPlayer());
        } else {
            try {
                String[] groups = perms.getPlayerGroups(null, offlinePlayer);
                return groups == null ? Arrays.asList() : Arrays.asList(groups);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error getting groups for offline player " + offlinePlayer.getName() + " via Vault", e);
                return Arrays.asList();
            }
        }
    }


    public String getPrimaryGroup(Player player) {
        if (!isVaultAvailable()) return null;
        try {
            return perms.getPrimaryGroup(player);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error getting primary group for player " + player.getName() + " via Vault", e);
            return null;
        }
    }

    public String getPrimaryGroup(UUID playerUuid) {
         if (!isVaultAvailable()) return null;
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUuid);
        if (offlinePlayer.isOnline()) {
            return getPrimaryGroup(offlinePlayer.getPlayer());
        } else {
             try {
                return perms.getPrimaryGroup(null, offlinePlayer);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error getting primary group for offline player " + offlinePlayer.getName() + " via Vault", e);
                return null;
            }
        }
    }
}
