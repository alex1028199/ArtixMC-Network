package com.example.diamondplugin.command;

import com.example.diamondplugin.DiamondPlugin;
import com.example.diamondplugin.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class EconomyCommands implements CommandExecutor, TabCompleter {

    private final DiamondPlugin plugin;
    private final EconomyManager economyManager;

    public EconomyCommands(DiamondPlugin plugin) {
        this.plugin = plugin;
        this.economyManager = plugin.getEconomyManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        switch (command.getName().toLowerCase()) {
            case "balance":
                return handleBalance(sender, args);
            case "pay":
                return handlePay(sender, args);
            case "eco":
                return handleEco(sender, args);
        }
        return false;
    }

    private boolean handleBalance(CommandSender sender, String[] args) {
        if (!sender.hasPermission("diamondplugin.command.balance")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        OfflinePlayer target;
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Console must specify a player.");
                return true;
            }
            target = (Player) sender;
        } else {
            if (!sender.hasPermission("diamondplugin.command.balance.others")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to check other players' balances.");
                return true;
            }
            target = Bukkit.getOfflinePlayer(args[0]);
            if (target == null || !economyManager.hasAccount(target.getUniqueId())) {
                 // Vault's hasAccount uses name, EconomyManager uses UUID.
                 // EconomyManager.hasAccount should be the source of truth if the player has played before.
                if(target == null || !target.hasPlayedBefore()){
                    sender.sendMessage(ChatColor.RED + "Player '" + args[0] + "' not found or has no account.");
                    return true;
                }
                 // If they have played before but no account in EconomyManager, it means it wasn't created on join or was removed.
                 // This check might be redundant if PlayerJoinListener ensures account creation.
                 if(!economyManager.hasAccount(target.getUniqueId())){
                     economyManager.createPlayerAccount(target.getUniqueId(), plugin.getEconomyConfig().getStartingBalance(), true);
                 }
            }
        }

        sender.sendMessage(ChatColor.YELLOW + target.getName() + "'s Balance: " + economyManager.format(economyManager.getBalance(target.getUniqueId())));
        return true;
    }

    private boolean handlePay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }
        if (!sender.hasPermission("diamondplugin.command.pay")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        // /pay <player> <amount>
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /pay <player> <amount>");
            return true;
        }

        Player payer = (Player) sender;
        OfflinePlayer payeeOffline = Bukkit.getOfflinePlayer(args[0]);

        if (payeeOffline == null || (!payeeOffline.hasPlayedBefore() && !payeeOffline.isOnline())) {
            sender.sendMessage(ChatColor.RED + "Player '" + args[0] + "' not found.");
            return true;
        }
        if (payeeOffline.getUniqueId().equals(payer.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "You cannot pay yourself.");
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid amount: " + args[1]);
            return true;
        }

        if (amount <= 0) {
            sender.sendMessage(ChatColor.RED + "Amount must be positive.");
            return true;
        }

        if (!economyManager.hasAccount(payer.getUniqueId())) {
             // Should not happen if join listener works
            economyManager.createPlayerAccount(payer.getUniqueId(), plugin.getEconomyConfig().getStartingBalance(), true);
        }
        if (!economyManager.hasAccount(payeeOffline.getUniqueId())) {
            economyManager.createPlayerAccount(payeeOffline.getUniqueId(), plugin.getEconomyConfig().getStartingBalance(), true);
             plugin.getLogger().info("Created account for " + payeeOffline.getName() + " during pay command.");
        }


        if (economyManager.withdrawPlayer(payer.getUniqueId(), amount)) {
            economyManager.depositPlayer(payeeOffline.getUniqueId(), amount);
            economyManager.saveBalances(); // Save after both transactions

            sender.sendMessage(ChatColor.GREEN + "You paid " + economyManager.format(amount) + " to " + payeeOffline.getName() + ".");
            if (payeeOffline.isOnline()) {
                ((Player) payeeOffline).sendMessage(ChatColor.GREEN + "You received " + economyManager.format(amount) + " from " + payer.getName() + ".");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "You do not have enough funds to make this payment.");
        }
        return true;
    }

    private boolean handleEco(CommandSender sender, String[] args) {
        if (!sender.hasPermission("diamondplugin.command.eco")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        // /eco <give|take|set|reset> <player> [amount]
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /eco <give|take|set|reset> <player> [amount]");
            return true;
        }

        String operation = args[0].toLowerCase();
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);

        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
             // Allow operations on known offline players if they have an account
            if(target == null || !economyManager.hasAccount(target.getUniqueId())) {
                 sender.sendMessage(ChatColor.RED + "Player '" + args[1] + "' not found or no account exists.");
                 return true;
            }
        }

        // Ensure account exists for target before operations, except for 'reset' which might create it.
        if (!operation.equals("reset") && !economyManager.hasAccount(target.getUniqueId())) {
             economyManager.createPlayerAccount(target.getUniqueId(), plugin.getEconomyConfig().getStartingBalance(), false); // don't save yet
        }


        double amount = 0;
        if (operation.equals("give") || operation.equals("take") || operation.equals("set")) {
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Usage: /eco " + operation + " <player> <amount>");
                return true;
            }
            try {
                amount = Double.parseDouble(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid amount: " + args[2]);
                return true;
            }
            if (amount < 0) {
                 sender.sendMessage(ChatColor.RED + "Amount cannot be negative.");
                 return true;
            }
        }

        switch (operation) {
            case "give":
                economyManager.depositPlayer(target.getUniqueId(), amount);
                sender.sendMessage(ChatColor.GREEN + "Gave " + economyManager.format(amount) + " to " + target.getName() + ".");
                if (target.isOnline()) ((Player)target).sendMessage(ChatColor.GREEN + "You received " + economyManager.format(amount) + " from an admin.");
                break;
            case "take":
                if (economyManager.withdrawPlayer(target.getUniqueId(), amount)) {
                    sender.sendMessage(ChatColor.GREEN + "Took " + economyManager.format(amount) + " from " + target.getName() + ".");
                     if (target.isOnline()) ((Player)target).sendMessage(ChatColor.RED + economyManager.format(amount) + " was taken from your account by an admin.");
                } else {
                    sender.sendMessage(ChatColor.RED + target.getName() + " does not have enough funds.");
                }
                break;
            case "set":
                economyManager.setBalance(target.getUniqueId(), amount);
                sender.sendMessage(ChatColor.GREEN + "Set " + target.getName() + "'s balance to " + economyManager.format(amount) + ".");
                if (target.isOnline()) ((Player)target).sendMessage(ChatColor.YELLOW + "Your balance was set to " + economyManager.format(amount) + " by an admin.");
                break;
            case "reset":
                 double startingBalance = plugin.getEconomyConfig().getStartingBalance();
                 economyManager.setBalance(target.getUniqueId(), startingBalance);
                 sender.sendMessage(ChatColor.GREEN + target.getName() + "'s balance has been reset to " + economyManager.format(startingBalance) + ".");
                 if (target.isOnline()) ((Player)target).sendMessage(ChatColor.YELLOW + "Your balance was reset by an admin.");
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown operation. Usage: /eco <give|take|set|reset> <player> [amount]");
                return true;
        }
        economyManager.saveBalances(); // Save changes after any eco operation
        return true;
    }


    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        String cmdName = command.getName().toLowerCase();

        if (cmdName.equals("balance")) {
            if (args.length == 1 && sender.hasPermission("diamondplugin.command.balance.others")) {
                Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                        .forEach(completions::add);
            }
        } else if (cmdName.equals("pay")) {
            if (args.length == 1) {
                Bukkit.getOnlinePlayers().stream()
                        .filter(p -> !p.getName().equals(sender.getName())) // Don't suggest self
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                        .forEach(completions::add);
            }
        } else if (cmdName.equals("eco")) {
            if (args.length == 1) {
                Arrays.asList("give", "take", "set", "reset").stream()
                        .filter(subCmd -> subCmd.startsWith(args[0].toLowerCase()))
                        .forEach(completions::add);
            } else if (args.length == 2) {
                Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .forEach(completions::add);
            }
        }
        return completions.stream().sorted().collect(Collectors.toList());
    }
}
