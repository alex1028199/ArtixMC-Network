package com.example.diamondplugin.economy;

import com.example.diamondplugin.DiamondPlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.UUID;

@SuppressWarnings("deprecation") // For OfflinePlayer names, Vault API itself uses deprecated methods
public class Economy_Diamond implements Economy {

    private final DiamondPlugin plugin;
    private final EconomyManager economyManager;

    public Economy_Diamond(DiamondPlugin plugin) {
        this.plugin = plugin;
        this.economyManager = plugin.getEconomyManager();
    }

    @Override
    public boolean isEnabled() {
        return plugin != null && plugin.isEnabled() && economyManager != null;
    }

    @Override
    public String getName() {
        return "DiamondPlugin Economy";
    }

    @Override
    public boolean hasBankSupport() {
        return false; // No bank support in this implementation
    }

    @Override
    public int fractionalDigits() {
        return 2; // Standard 2 decimal places
    }

    @Override
    public String format(double amount) {
        return economyManager.format(amount);
    }

    @Override
    public String currencyNamePlural() {
        return plugin.getEconomyConfig().getCurrencyNamePlural();
    }

    @Override
    public String currencyNameSingular() {
        return plugin.getEconomyConfig().getCurrencyNameSingular();
    }

    @Override
    public boolean hasAccount(String playerName) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        return player != null && economyManager.hasAccount(player.getUniqueId());
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return player != null && economyManager.hasAccount(player.getUniqueId());
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName); // World specific accounts not supported
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player); // World specific accounts not supported
    }

    @Override
    public double getBalance(String playerName) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        if (player == null) return 0.0;
        return economyManager.getBalance(player.getUniqueId());
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        if (player == null) return 0.0;
        return economyManager.getBalance(player.getUniqueId());
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(String playerName, double amount) {
        return getBalance(playerName) >= amount;
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        if (player == null) return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Player not found.");
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (player == null) return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Player cannot be null.");
        if (amount < 0) return new EconomyResponse(0, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Cannot withdraw negative amount.");

        if (economyManager.withdrawPlayer(player.getUniqueId(), amount)) {
            economyManager.saveBalances(); // Ensure data is saved
            return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, null);
        } else {
            return new EconomyResponse(0, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Insufficient funds.");
        }
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        if (player == null) return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Player not found.");
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (player == null) return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Player cannot be null.");
        if (amount < 0) return new EconomyResponse(0, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Cannot deposit negative amount.");

        economyManager.depositPlayer(player.getUniqueId(), amount);
        economyManager.saveBalances(); // Ensure data is saved
        return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    // For Vault, "bank" usually refers to player-specific virtual bank accounts if the econ supports it,
    // not named, shared physical banks. So 'name' parameter is often ignored or treated as player identifier.
    // We'll assume player-specific bank accounts. The 'name' for bank operations will be the player's name.

    @Override
    public EconomyResponse createBank(String name, String player) { // 'name' is bank name, 'player' is owner
         // In our system, players don't "create" named banks. They just have a personal bank balance.
         // This method isn't directly applicable to our current simple bank balance system.
         // We can interpret 'name' as the player themselves for hasAccount check.
        OfflinePlayer owner = Bukkit.getOfflinePlayer(player);
        if (owner == null) return new EconomyResponse(0,0, EconomyResponse.ResponseType.FAILURE, "Bank owner not found.");
        // If they have an account, their "bank" implicitly exists.
        return economyManager.hasAccount(owner.getUniqueId()) ?
            new EconomyResponse(0, economyManager.getBankBalance(owner.getUniqueId()), EconomyResponse.ResponseType.SUCCESS, "Bank account already exists.") :
            new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Player account needed to have a bank account.");
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        if (player == null) return new EconomyResponse(0,0, EconomyResponse.ResponseType.FAILURE, "Bank owner cannot be null.");
        return economyManager.hasAccount(player.getUniqueId()) ?
            new EconomyResponse(0, economyManager.getBankBalance(player.getUniqueId()), EconomyResponse.ResponseType.SUCCESS, "Bank account already exists.") :
            new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Player account needed to have a bank account.");
    }


    @Override
    public EconomyResponse deleteBank(String name) { // 'name' is player name for our context
        OfflinePlayer player = Bukkit.getOfflinePlayer(name);
        if (player == null || !economyManager.hasAccount(player.getUniqueId())) {
            return new EconomyResponse(0,0, EconomyResponse.ResponseType.FAILURE, "No account found for this player to delete bank balance.");
        }
        economyManager.setBankBalance(player.getUniqueId(), 0.0); // Reset bank balance
        economyManager.saveBalances();
        return new EconomyResponse(0,0, EconomyResponse.ResponseType.SUCCESS, "Bank balance reset for " + name);
    }

    @Override
    public EconomyResponse bankBalance(String name) { // 'name' is player name
        OfflinePlayer player = Bukkit.getOfflinePlayer(name);
        if (player == null || !economyManager.hasAccount(player.getUniqueId())) {
            return new EconomyResponse(0,0, EconomyResponse.ResponseType.FAILURE, "No bank account found.");
        }
        return new EconomyResponse(0, economyManager.getBankBalance(player.getUniqueId()), EconomyResponse.ResponseType.SUCCESS, "");
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) { // 'name' is player name
        OfflinePlayer player = Bukkit.getOfflinePlayer(name);
        if (player == null || !economyManager.hasAccount(player.getUniqueId())) {
             return new EconomyResponse(0,0, EconomyResponse.ResponseType.FAILURE, "No bank account found.");
        }
        return economyManager.getBankBalance(player.getUniqueId()) >= amount ?
            new EconomyResponse(0, economyManager.getBankBalance(player.getUniqueId()), EconomyResponse.ResponseType.SUCCESS, "") :
            new EconomyResponse(0, economyManager.getBankBalance(player.getUniqueId()), EconomyResponse.ResponseType.FAILURE, "Insufficient funds.");
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) { // 'name' is player name
        OfflinePlayer player = Bukkit.getOfflinePlayer(name);
        if (player == null) return new EconomyResponse(0,0, EconomyResponse.ResponseType.FAILURE, "Player not found.");
        if (amount < 0) return new EconomyResponse(0, economyManager.getBankBalance(player.getUniqueId()), EconomyResponse.ResponseType.FAILURE, "Cannot withdraw negative amount.");

        if (economyManager.withdrawFromBank(player.getUniqueId(), amount)) {
            economyManager.saveBalances();
            return new EconomyResponse(amount, economyManager.getBankBalance(player.getUniqueId()), EconomyResponse.ResponseType.SUCCESS, "");
        } else {
            return new EconomyResponse(0, economyManager.getBankBalance(player.getUniqueId()), EconomyResponse.ResponseType.FAILURE, "Insufficient funds in bank or other error.");
        }
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) { // 'name' is player name
        OfflinePlayer player = Bukkit.getOfflinePlayer(name);
        if (player == null) return new EconomyResponse(0,0, EconomyResponse.ResponseType.FAILURE, "Player not found.");
         if (amount < 0) return new EconomyResponse(0, economyManager.getBankBalance(player.getUniqueId()), EconomyResponse.ResponseType.FAILURE, "Cannot deposit negative amount.");

        if (economyManager.depositToBank(player.getUniqueId(), amount)) {
            economyManager.saveBalances();
            return new EconomyResponse(amount, economyManager.getBankBalance(player.getUniqueId()), EconomyResponse.ResponseType.SUCCESS, "");
        } else {
            // This typically means not enough in main balance to transfer to bank
            return new EconomyResponse(0, economyManager.getBankBalance(player.getUniqueId()), EconomyResponse.ResponseType.FAILURE, "Insufficient funds in main balance or other error.");
        }
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) { // 'name' is player name for bank context
        OfflinePlayer owner = Bukkit.getOfflinePlayer(playerName);
        if (owner != null && name.equalsIgnoreCase(owner.getName()) && economyManager.hasAccount(owner.getUniqueId())) {
            return new EconomyResponse(0,0, EconomyResponse.ResponseType.SUCCESS, "");
        }
        return new EconomyResponse(0,0, EconomyResponse.ResponseType.FAILURE, "Not the bank owner or no account.");
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
         if (player != null && name.equalsIgnoreCase(player.getName()) && economyManager.hasAccount(player.getUniqueId())) {
            return new EconomyResponse(0,0, EconomyResponse.ResponseType.SUCCESS, "");
        }
        return new EconomyResponse(0,0, EconomyResponse.ResponseType.FAILURE, "Not the bank owner or no account.");
    }


    @Override
    public EconomyResponse isBankMember(String name, String playerName) { // 'name' is player name for bank context
        // For a personal bank, the owner is the only member.
        return isBankOwner(name, playerName);
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return isBankOwner(name, player);
    }

    @Override
    public List<String> getBanks() {
        return List.of();
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        return player != null && createPlayerAccount(player);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        if (player == null) return false;
        return economyManager.createPlayerAccount(player.getUniqueId(), plugin.getEconomyConfig().getStartingBalance(), true);
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }
}
