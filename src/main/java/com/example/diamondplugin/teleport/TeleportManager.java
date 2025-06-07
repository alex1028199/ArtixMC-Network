package com.example.diamondplugin.teleport;

import com.example.diamondplugin.DiamondPlugin;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class TeleportManager {

    private final DiamondPlugin plugin;
    // TeleportConfig is accessed via plugin.getTeleportConfig() if needed for delays/cooldowns

    public TeleportManager(DiamondPlugin plugin) {
        this.plugin = plugin;
    }

    public void setSpawnLocation(Location location) {
        plugin.getConfig().set("teleportation.spawn.world", location.getWorld().getName());
        plugin.getConfig().set("teleportation.spawn.x", location.getX());
        plugin.getConfig().set("teleportation.spawn.y", location.getY());
        plugin.getConfig().set("teleportation.spawn.z", location.getZ());
        plugin.getConfig().set("teleportation.spawn.yaw", location.getYaw());
        plugin.getConfig().set("teleportation.spawn.pitch", location.getPitch());
        plugin.saveConfig(); // Save changes to config.yml
        // Also update the loaded TeleportConfig instance
        plugin.getTeleportConfig().loadConfig();
    }

    public Location getSpawnLocation() {
        String worldName = plugin.getTeleportConfig().getSpawnWorld();
        if (worldName == null || plugin.getServer().getWorld(worldName) == null) {
            plugin.getLogger().warning("Spawn world '" + worldName + "' not found or not specified. Defaulting to main world spawn or null.");
            // Attempt to get the main world's spawn if possible, otherwise return null or a very default Location
            if (plugin.getServer().getWorlds().size() > 0) {
                return plugin.getServer().getWorlds().get(0).getSpawnLocation();
            }
            return null;
        }
        return new Location(
                plugin.getServer().getWorld(worldName),
                plugin.getTeleportConfig().getSpawnX(),
                plugin.getTeleportConfig().getSpawnY(),
                plugin.getTeleportConfig().getSpawnZ(),
                plugin.getTeleportConfig().getSpawnYaw(),
                plugin.getTeleportConfig().getSpawnPitch()
        );
    }

    public boolean setPlayerHome(UUID playerUuid, String homeName, Location location) {
        String basePath = "homes." + playerUuid.toString() + "." + homeName.toLowerCase();
        plugin.getHomesConfig().set(basePath + ".world", location.getWorld().getName());
        plugin.getHomesConfig().set(basePath + ".x", location.getX());
        plugin.getHomesConfig().set(basePath + ".y", location.getY());
        plugin.getHomesConfig().set(basePath + ".z", location.getZ());
        plugin.getHomesConfig().set(basePath + ".yaw", location.getYaw());
        plugin.getHomesConfig().set(basePath + ".pitch", location.getPitch());
        plugin.saveHomesConfig();
        return true;
    }

    public Location getPlayerHome(UUID playerUuid, String homeName) {
        String basePath = "homes." + playerUuid.toString() + "." + homeName.toLowerCase();
        if (!plugin.getHomesConfig().contains(basePath)) {
            return null;
        }
        String worldName = plugin.getHomesConfig().getString(basePath + ".world");
        if (worldName == null || plugin.getServer().getWorld(worldName) == null) {
            plugin.getLogger().warning("World " + worldName + " for home " + homeName + " of player " + playerUuid + " not found.");
            return null;
        }
        double x = plugin.getHomesConfig().getDouble(basePath + ".x");
        double y = plugin.getHomesConfig().getDouble(basePath + ".y");
        double z = plugin.getHomesConfig().getDouble(basePath + ".z");
        float yaw = (float) plugin.getHomesConfig().getDouble(basePath + ".yaw");
        float pitch = (float) plugin.getHomesConfig().getDouble(basePath + ".pitch");

        return new Location(plugin.getServer().getWorld(worldName), x, y, z, yaw, pitch);
    }

    public boolean deletePlayerHome(UUID playerUuid, String homeName) {
        String basePath = "homes." + playerUuid.toString() + "." + homeName.toLowerCase();
        if (plugin.getHomesConfig().contains(basePath)) {
            plugin.getHomesConfig().set(basePath, null); // Remove the home section
            // Check if the player has any homes left, if not, remove the player UUID section
            ConfigurationSection playerHomesSection = plugin.getHomesConfig().getConfigurationSection("homes." + playerUuid.toString());
            if (playerHomesSection != null && playerHomesSection.getKeys(false).isEmpty()) {
                plugin.getHomesConfig().set("homes." + playerUuid.toString(), null);
            }
            plugin.saveHomesConfig();
            return true;
        }
        return false;
    }

    public List<String> getPlayerHomeNames(UUID playerUuid) {
        String basePath = "homes." + playerUuid.toString();
        ConfigurationSection playerHomesSection = plugin.getHomesConfig().getConfigurationSection(basePath);
        if (playerHomesSection == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(playerHomesSection.getKeys(false));
    }

    public Map<String, Location> getPlayerHomes(UUID playerUuid) {
        Map<String, Location> homes = new HashMap<>();
        List<String> homeNames = getPlayerHomeNames(playerUuid);
        for (String homeName : homeNames) {
            Location loc = getPlayerHome(playerUuid, homeName);
            if (loc != null) {
                homes.put(homeName, loc);
            }
        }
        return homes;
    }


    public int getMaxHomes(Player player) {
        if (player.hasPermission("diamondplugin.homes.limit.unlimited")) {
            return Integer.MAX_VALUE;
        }
        // Iterate through permissions like diamondplugin.homes.limit.5, diamondplugin.homes.limit.10
        // This is a common way; can be made more efficient if many such perms exist.
        int max = plugin.getConfig().getInt("teleportation.homes.default-max", 1); // Default from config
        for (int i = 100; i > 0; i--) { // Check from a reasonable high number downwards
            if (player.hasPermission("diamondplugin.homes.limit." + i)) {
                return i;
            }
        }
        return max;
    }
}
