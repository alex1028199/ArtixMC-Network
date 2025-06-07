package com.example.diamondplugin.config;

import com.example.diamondplugin.DiamondPlugin;
import org.bukkit.configuration.file.FileConfiguration;

public class TeleportConfig {

    private final DiamondPlugin plugin;

    private int defaultMaxHomes;
    private boolean allowMultipleHomes;
    private String defaultHomeName;
    private int homeTeleportDelaySeconds;
    private int homeTeleportCooldownSeconds;

    // Spawn settings
    private String spawnWorld;
    private double spawnX;
    private double spawnY;
    private double spawnZ;
    private float spawnYaw;
    private float spawnPitch;
    private int spawnTeleportDelaySeconds;
    private int spawnTeleportCooldownSeconds;

    // TPA settings
    private int tpaRequestTimeoutSeconds;
    private int tpaTeleportDelaySeconds;
    // private int tpaCooldownSeconds; // Optional: cooldown for sending /tpa


    public TeleportConfig(DiamondPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        FileConfiguration config = plugin.getConfig();

        config.addDefault("teleportation.homes.default-max", 1);
        config.addDefault("teleportation.homes.allow-multiple", true);
        config.addDefault("teleportation.homes.default-name", "home");
        config.addDefault("teleportation.homes.teleport-delay-seconds", 3);
        config.addDefault("teleportation.homes.teleport-cooldown-seconds", 60);

        // Spawn defaults
        config.addDefault("teleportation.spawn.world", "world"); // Assuming default world name
        config.addDefault("teleportation.spawn.x", 0.5);
        config.addDefault("teleportation.spawn.y", 64.0);
        config.addDefault("teleportation.spawn.z", 0.5);
        config.addDefault("teleportation.spawn.yaw", 0.0);
        config.addDefault("teleportation.spawn.pitch", 0.0);
        config.addDefault("teleportation.spawn.teleport-delay-seconds", 3); // Can use home/spawn delay or be specific
        config.addDefault("teleportation.spawn.teleport-cooldown-seconds", 30);

        // TPA defaults
        config.addDefault("teleportation.tpa.request-timeout-seconds", 60);
        config.addDefault("teleportation.tpa.teleport-delay-seconds", 3);
        // config.addDefault("teleportation.tpa.cooldown-seconds", 60);


        // plugin.getConfig().options().copyDefaults(true); // Done in DiamondPlugin onEnable
        // plugin.saveConfig(); // Done in DiamondPlugin onEnable after all configs add defaults

        defaultMaxHomes = config.getInt("teleportation.homes.default-max", 1);
        allowMultipleHomes = config.getBoolean("teleportation.homes.allow-multiple", true);
        defaultHomeName = config.getString("teleportation.homes.default-name", "home");
        homeTeleportDelaySeconds = config.getInt("teleportation.homes.teleport-delay-seconds", 3);
        homeTeleportCooldownSeconds = config.getInt("teleportation.homes.teleport-cooldown-seconds", 60);

        spawnWorld = config.getString("teleportation.spawn.world", "world");
        spawnX = config.getDouble("teleportation.spawn.x", 0.5);
        spawnY = config.getDouble("teleportation.spawn.y", 64.0);
        spawnZ = config.getDouble("teleportation.spawn.z", 0.5);
        spawnYaw = (float) config.getDouble("teleportation.spawn.yaw", 0.0);
        spawnPitch = (float) config.getDouble("teleportation.spawn.pitch", 0.0);
        spawnTeleportDelaySeconds = config.getInt("teleportation.spawn.teleport-delay-seconds", 3);
        spawnTeleportCooldownSeconds = config.getInt("teleportation.spawn.teleport-cooldown-seconds", 30);

        tpaRequestTimeoutSeconds = config.getInt("teleportation.tpa.request-timeout-seconds", 60);
        tpaTeleportDelaySeconds = config.getInt("teleportation.tpa.teleport-delay-seconds", 3);
        // tpaCooldownSeconds = config.getInt("teleportation.tpa.cooldown-seconds", 60);
    }

    public int getDefaultMaxHomes() {
        return defaultMaxHomes;
    }

    public boolean isAllowMultipleHomes() {
        return allowMultipleHomes;
    }

    public String getDefaultHomeName() {
        return defaultHomeName;
    }

    public int getHomeTeleportDelaySeconds() {
        return homeTeleportDelaySeconds;
    }

    public int getHomeTeleportCooldownSeconds() {
        return homeTeleportCooldownSeconds;
    }

    // Spawn config getters
    public String getSpawnWorld() {
        return spawnWorld;
    }

    public double getSpawnX() {
        return spawnX;
    }

    public double getSpawnY() {
        return spawnY;
    }

    public double getSpawnZ() {
        return spawnZ;
    }

    public float getSpawnYaw() {
        return spawnYaw;
    }

    public float getSpawnPitch() {
        return spawnPitch;
    }

    public int getSpawnTeleportDelaySeconds() {
        return spawnTeleportDelaySeconds;
    }

    public int getSpawnTeleportCooldownSeconds() {
        return spawnTeleportCooldownSeconds;
    }

    // TPA getters
    public int getTpaRequestTimeoutSeconds() {
        return tpaRequestTimeoutSeconds;
    }

    public int getTpaTeleportDelaySeconds() {
        return tpaTeleportDelaySeconds;
    }

    // public int getTpaCooldownSeconds() {
    //     return tpaCooldownSeconds;
    // }
}
