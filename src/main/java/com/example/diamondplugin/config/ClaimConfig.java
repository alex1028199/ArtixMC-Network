package com.example.diamondplugin.config;

import com.example.diamondplugin.DiamondPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

public class ClaimConfig {

    private final DiamondPlugin plugin;

    private Material claimTool;
    private int minClaimSize;
    private int defaultMaxClaimsPerPlayer;
    // private double costPerBlock; // For future economy integration
    // private int initialClaimBlocks; // For a claim block system
    private String msgCannotBreak;
    private String msgCannotPlace;

    public ClaimConfig(DiamondPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        FileConfiguration config = plugin.getConfig();

        config.addDefault("claiming.tool-item", "GOLDEN_SHOVEL");
        config.addDefault("claiming.min-claim-size", 10);
        config.addDefault("claiming.max-claims-per-player", 3);
        // config.addDefault("claiming.cost-per-block", 0.1);
        // config.addDefault("claiming.initial-claim-blocks", 1000);
        config.addDefault("claiming.messages.cannot-break", "&cYou can't break blocks here. This land is claimed by {owner}.");
        config.addDefault("claiming.messages.cannot-place", "&cYou can't place blocks here. This land is claimed by {owner}.");

        // Note: copyDefaults and saveConfig should be called in DiamondPlugin's onEnable
        // after all configs have added their defaults.

        String toolItemName = config.getString("claiming.tool-item", "GOLDEN_SHOVEL").toUpperCase();
        try {
            claimTool = Material.valueOf(toolItemName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid claiming tool item '" + toolItemName + "' in config.yml. Defaulting to GOLDEN_SHOVEL.");
            claimTool = Material.GOLDEN_SHOVEL;
        }

        minClaimSize = config.getInt("claiming.min-claim-size", 10);
        defaultMaxClaimsPerPlayer = config.getInt("claiming.max-claims-per-player", 3);
        // costPerBlock = config.getDouble("claiming.cost-per-block", 0.1);
        // initialClaimBlocks = config.getInt("claiming.initial-claim-blocks", 1000);
        msgCannotBreak = config.getString("claiming.messages.cannot-break", "&cYou can't break blocks here. This land is claimed by {owner}.");
        msgCannotPlace = config.getString("claiming.messages.cannot-place", "&cYou can't place blocks here. This land is claimed by {owner}.");
    }

    public Material getClaimTool() {
        return claimTool;
    }

    public int getMinClaimSize() {
        if (minClaimSize <=0) return 1; // Ensure positive
        return minClaimSize;
    }

    public int getDefaultMaxClaimsPerPlayer() {
        if (defaultMaxClaimsPerPlayer < 0) return 0;
        return defaultMaxClaimsPerPlayer;
    }

    // public double getCostPerBlock() {
    //     return costPerBlock;
    // }

    // public int getInitialClaimBlocks() {
    //     return initialClaimBlocks;
    // }

    public String getMsgCannotBreak() {
        return msgCannotBreak;
    }

    public String getMsgCannotPlace() {
        return msgCannotPlace;
    }
}
