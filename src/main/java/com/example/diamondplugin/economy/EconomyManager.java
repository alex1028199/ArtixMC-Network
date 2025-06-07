package com.example.diamondplugin.economy;

import com.example.diamondplugin.DiamondPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class EconomyManager {

    private final DiamondPlugin plugin;
    private final Map<UUID, Double> balances = new HashMap<>();
    private final Map<UUID, Double> bankBalances = new HashMap<>(); // Added for bank balances
    private final DecimalFormat currencyFormatter;

    public EconomyManager(DiamondPlugin plugin) {
        this.plugin = plugin;
        String symbol = plugin.getEconomyConfig().getCurrencySymbol();
        // More robust formatter could be added, e.g., for specific locales or decimal places
        this.currencyFormatter = new DecimalFormat(symbol + "#,##0.00");
        loadBalances();
    }

    public void loadBalances() {
        balances.clear();
        bankBalances.clear(); // Clear bank balances too
        FileConfiguration playerDataConfig = plugin.getPlayerDataConfig();
        if (playerDataConfig.isConfigurationSection("players")) {
            ConfigurationSection playersSection = playerDataConfig.getConfigurationSection("players");
            for (String uuidStr : playersSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    boolean accountExisted = playersSection.contains(uuidStr + ".balance");
                    double balance = playersSection.getDouble(uuidStr + ".balance", plugin.getEconomyConfig().getStartingBalance());
                    double bankBalance = playersSection.getDouble(uuidStr + ".bank_balance", 0.0);

                    balances.put(uuid, balance);
                    bankBalances.put(uuid, bankBalance);

                    if (!accountExisted) {
                         plugin.getLogger().info("Player " + uuidStr + " had no main balance, assigned starting balance. Bank balance set to " + bankBalance);
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in playerdata.yml for economy: " + uuidStr);
                }
            }
        }
        plugin.getLogger().info("Loaded " + balances.size() + " player balances and " + bankBalances.size() + " bank balances.");
    }

    public void saveBalances() {
        FileConfiguration playerDataConfig = plugin.getPlayerDataConfig();
        ConfigurationSection playersSection = playerDataConfig.getConfigurationSection("players");
        if (playersSection == null) {
            playersSection = playerDataConfig.createSection("players");
        }

        // Save main balances
        for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
            playersSection.set(entry.getKey().toString() + ".balance", entry.getValue());
        }
        // Save bank balances
        for (Map.Entry<UUID, Double> entry : bankBalances.entrySet()) {
            playersSection.set(entry.getKey().toString() + ".bank_balance", entry.getValue());
        }

        plugin.savePlayerDataConfig();
        plugin.getLogger().info("Saved " + balances.size() + " player balances and " + bankBalances.size() + " bank balances.");
    }

    public double getBalance(UUID playerUuid) {
        // Ensure account exists if trying to get balance, default to 0 if not.
        if (!hasAccount(playerUuid)) {
            createPlayerAccount(playerUuid, plugin.getEconomyConfig().getStartingBalance(), true); // Create with starting balance and save
            return plugin.getEconomyConfig().getStartingBalance(); // Return the starting balance
        }
        return balances.getOrDefault(playerUuid, 0.0);
    }

    public void setBalance(UUID playerUuid, double amount) {
        if (amount < 0) amount = 0;
        balances.put(playerUuid, amount);
    }

    public double getBankBalance(UUID playerUuid) {
        if (!hasAccount(playerUuid)) { // Ensure main account exists before bank account
             createPlayerAccount(playerUuid, plugin.getEconomyConfig().getStartingBalance(), true);
        }
        return bankBalances.getOrDefault(playerUuid, 0.0);
    }

    public void setBankBalance(UUID playerUuid, double amount) {
        if (amount < 0) amount = 0;
        if (!hasAccount(playerUuid)) { // Ensure main account exists
             createPlayerAccount(playerUuid, plugin.getEconomyConfig().getStartingBalance(), false); // Don't save yet, setBankBalance will
        }
        bankBalances.put(playerUuid, amount);
    }

    public boolean hasAccount(UUID playerUuid) {
        // An account is considered to exist if there's a main balance entry.
        // Bank balance is secondary.
        return balances.containsKey(playerUuid);
    }

    public boolean createPlayerAccount(UUID playerUuid, double startingBalance, boolean saveImmediately) {
        if (hasAccount(playerUuid)) {
            return false; // Account already exists
        }
        balances.put(playerUuid, startingBalance);
        bankBalances.put(playerUuid, 0.0); // Initialize bank balance to 0
        if (saveImmediately) {
            saveBalances();
        }
        return true;
    }
     public boolean createPlayerAccount(UUID playerUuid, boolean saveImmediately) {
        return createPlayerAccount(playerUuid, plugin.getEconomyConfig().getStartingBalance(), saveImmediately);
    }


    public boolean depositPlayer(UUID playerUuid, double amount) { // Deposits to main balance
        if (amount <= 0) return false;
        if (!hasAccount(playerUuid)) {
            createPlayerAccount(playerUuid, plugin.getEconomyConfig().getStartingBalance() + amount, false);
            return true;
        }
        balances.put(playerUuid, getBalance(playerUuid) + amount);
        return true;
    }

    public boolean withdrawPlayer(UUID playerUuid, double amount) { // Withdraws from main balance
        if (amount <= 0) return false;
        if (!hasAccount(playerUuid)) return false;

        double currentBalance = getBalance(playerUuid);
        if (currentBalance < amount) {
            return false;
        }
        balances.put(playerUuid, currentBalance - amount);
        return true;
    }

    public boolean depositToBank(UUID playerUuid, double amount) {
        if (amount <= 0) return false;
        if (!withdrawPlayer(playerUuid, amount)) { // Attempt to withdraw from main balance
            return false; // Not enough in main balance
        }
        // If withdrawPlayer was successful, main balance is updated in memory
        bankBalances.put(playerUuid, getBankBalance(playerUuid) + amount);
        // Save all balances after successful operation
        // saveBalances(); // Removed to allow batch saving by commands
        return true;
    }

    public boolean withdrawFromBank(UUID playerUuid, double amount) {
        if (amount <= 0) return false;
        double currentBankBalance = getBankBalance(playerUuid);
        if (currentBankBalance < amount) {
            return false; // Not enough in bank balance
        }
        bankBalances.put(playerUuid, currentBankBalance - amount);
        depositPlayer(playerUuid, amount); // Deposit into main balance (this updates balances map)
        // Save all balances after successful operation
        // saveBalances(); // Removed to allow batch saving by commands
        return true;
    }

    public String format(double amount) {
        return currencyFormatter.format(amount);
    }
}
