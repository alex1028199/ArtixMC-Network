package com.example.diamondplugin.config;

import com.example.diamondplugin.DiamondPlugin;
import org.bukkit.configuration.file.FileConfiguration;

public class EconomyConfig {

    private final DiamondPlugin plugin;

    private String currencyNameSingular;
    private String currencyNamePlural;
    private String currencySymbol;
    private double startingBalance;
    // private boolean payCommandEnabled; // Future use
    // private boolean ecoCommandEnabled; // Future use

    public EconomyConfig(DiamondPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        FileConfiguration config = plugin.getConfig();

        config.addDefault("economy.currency-name-singular", "Dollar");
        config.addDefault("economy.currency-name-plural", "Dollars");
        config.addDefault("economy.currency-symbol", "$");
        config.addDefault("economy.starting-balance", 100.0);
        // config.addDefault("economy.pay-command-enabled", true);
        // config.addDefault("economy.eco-command-enabled", true);

        // Note: copyDefaults and saveConfig should be called in DiamondPlugin's onEnable
        // after all configs have added their defaults.

        currencyNameSingular = config.getString("economy.currency-name-singular", "Dollar");
        currencyNamePlural = config.getString("economy.currency-name-plural", "Dollars");
        currencySymbol = config.getString("economy.currency-symbol", "$");
        startingBalance = config.getDouble("economy.starting-balance", 100.0);
        // payCommandEnabled = config.getBoolean("economy.pay-command-enabled", true);
        // ecoCommandEnabled = config.getBoolean("economy.eco-command-enabled", true);
    }

    public String getCurrencyNameSingular() {
        return currencyNameSingular;
    }

    public String getCurrencyNamePlural() {
        return currencyNamePlural;
    }

    public String getCurrencySymbol() {
        return currencySymbol;
    }

    public double getStartingBalance() {
        return startingBalance;
    }

    // public boolean isPayCommandEnabled() {
    //     return payCommandEnabled;
    // }

    // public boolean isEcoCommandEnabled() {
    //     return ecoCommandEnabled;
    // }
}
