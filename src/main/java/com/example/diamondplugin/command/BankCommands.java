package com.example.diamondplugin.command;

import com.example.diamondplugin.DiamondPlugin;
import com.example.diamondplugin.economy.EconomyManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors; // Added import

public class BankCommands implements CommandExecutor, TabCompleter {

    private final DiamondPlugin plugin;
    private final EconomyManager economyManager;
    // Placeholder for potential future config options for bank if needed
    // private final com.example.diamondplugin.config.EconomyConfig economyConfig;

    public BankCommands(DiamondPlugin plugin) {
        this.plugin = plugin;
        this.economyManager = plugin.getEconomyManager();
        // this.economyConfig = plugin.getEconomyConfig();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            // Base /bank command - show balances and help
            if (!player.hasPermission("diamondplugin.command.bank.info") && !player.hasPermission("diamondplugin.command.bank.deposit") && !player.hasPermission("diamondplugin.command.bank.withdraw")) {
                 player.sendMessage(ChatColor.RED + "You don't have permission to use bank commands.");
                 return true;
            }
            double mainBalance = economyManager.getBalance(player.getUniqueId());
            double bankBalance = economyManager.getBankBalance(player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "--- Your Bank ---");
            player.sendMessage(ChatColor.GOLD + "Main Balance: " + ChatColor.WHITE + economyManager.format(mainBalance));
            player.sendMessage(ChatColor.GOLD + "Bank Balance: " + ChatColor.WHITE + economyManager.format(bankBalance));
            player.sendMessage(ChatColor.GRAY + "Usage: /bank <deposit|withdraw> <amount|all>");
            return true;
        }

        String subCommand = args[0].toLowerCase();
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /bank <deposit|withdraw> <amount|all>");
            return true;
        }

        String amountStr = args[1];
        double amount;
        boolean all = amountStr.equalsIgnoreCase("all");

        switch (subCommand) {
            case "deposit":
                if (!player.hasPermission("diamondplugin.command.bank.deposit")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to deposit funds.");
                    return true;
                }
                if (all) {
                    amount = economyManager.getBalance(player.getUniqueId());
                } else {
                    try {
                        amount = Double.parseDouble(amountStr);
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "Invalid amount: " + amountStr);
                        return true;
                    }
                }
                if (amount <= 0) {
                    player.sendMessage(ChatColor.RED + "Deposit amount must be positive.");
                    return true;
                }
                if (economyManager.depositToBank(player.getUniqueId(), amount)) {
                    player.sendMessage(ChatColor.GREEN + "Successfully deposited " + economyManager.format(amount) + " to your bank.");
                    player.sendMessage(ChatColor.GRAY + "New main balance: " + economyManager.format(economyManager.getBalance(player.getUniqueId())));
                    player.sendMessage(ChatColor.GRAY + "New bank balance: " + economyManager.format(economyManager.getBankBalance(player.getUniqueId())));
                    economyManager.saveBalances(); // Save after transaction
                } else {
                    player.sendMessage(ChatColor.RED + "Insufficient funds in your main balance to deposit " + economyManager.format(amount) + ".");
                }
                break;

            case "withdraw":
                if (!player.hasPermission("diamondplugin.command.bank.withdraw")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to withdraw funds.");
                    return true;
                }
                if (all) {
                    amount = economyManager.getBankBalance(player.getUniqueId());
                } else {
                    try {
                        amount = Double.parseDouble(amountStr);
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "Invalid amount: " + amountStr);
                        return true;
                    }
                }
                if (amount <= 0) {
                    player.sendMessage(ChatColor.RED + "Withdrawal amount must be positive.");
                    return true;
                }
                if (economyManager.withdrawFromBank(player.getUniqueId(), amount)) {
                    player.sendMessage(ChatColor.GREEN + "Successfully withdrew " + economyManager.format(amount) + " from your bank.");
                    player.sendMessage(ChatColor.GRAY + "New main balance: " + economyManager.format(economyManager.getBalance(player.getUniqueId())));
                    player.sendMessage(ChatColor.GRAY + "New bank balance: " + economyManager.format(economyManager.getBankBalance(player.getUniqueId())));
                    economyManager.saveBalances(); // Save after transaction
                } else {
                    player.sendMessage(ChatColor.RED + "Insufficient funds in your bank balance to withdraw " + economyManager.format(amount) + ".");
                }
                break;

            default:
                player.sendMessage(ChatColor.RED + "Usage: /bank <deposit|withdraw> <amount|all>");
                break;
        }
        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player)) return Collections.emptyList();
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            if ("deposit".startsWith(args[0].toLowerCase()) && sender.hasPermission("diamondplugin.command.bank.deposit")) completions.add("deposit");
            if ("withdraw".startsWith(args[0].toLowerCase()) && sender.hasPermission("diamondplugin.command.bank.withdraw")) completions.add("withdraw");
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("deposit") || args[0].equalsIgnoreCase("withdraw")) {
                if ("all".startsWith(args[1].toLowerCase())) completions.add("all");
                // Could suggest amounts like 100, 500, 1000 if desired
            }
        }
        return completions.stream().sorted().collect(Collectors.toList());
    }
}
