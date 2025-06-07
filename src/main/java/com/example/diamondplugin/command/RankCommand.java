package com.example.diamondplugin.command;

import com.example.diamondplugin.DiamondPlugin;
import com.example.diamondplugin.rank.Rank;
import com.example.diamondplugin.rank.RankManager;
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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class RankCommand implements CommandExecutor, TabCompleter {

    private final DiamondPlugin plugin;
    private final RankManager rankManager;

    public RankCommand(DiamondPlugin plugin) {
        this.plugin = plugin;
        this.rankManager = plugin.getRankManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank <create|delete|setprefix|set|remove|list>");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create":
                handleCreate(sender, args);
                break;
            case "delete":
                handleDelete(sender, args);
                break;
            case "setprefix":
                handleSetPrefix(sender, args);
                break;
            case "set":
                handleSet(sender, args);
                break;
            case "remove":
                handleRemove(sender, args);
                break;
            case "list":
                handleList(sender);
                break;
            case "checkexpiry":
                handleCheckExpiry(sender, args);
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown sub-command. Usage: /rank <create|delete|setprefix|set|remove|list|checkexpiry>");
                break;
        }
        return true;
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("diamondplugin.rank.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank create <name> [prefix]");
            return;
        }
        String rankName = args[1];
        String prefix = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "&7[" + rankName + "] ";

        if (rankManager.createRank(rankName, prefix)) {
            sender.sendMessage(ChatColor.GREEN + "Rank '" + rankName + "' created with prefix '" + prefix + ChatColor.GREEN + "'.");
        } else {
            sender.sendMessage(ChatColor.RED + "Rank '" + rankName + "' already exists.");
        }
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("diamondplugin.rank.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank delete <name>");
            return;
        }
        String rankName = args[1];
        if (rankManager.deleteRank(rankName)) {
            sender.sendMessage(ChatColor.GREEN + "Rank '" + rankName + "' deleted.");
        } else {
            sender.sendMessage(ChatColor.RED + "Rank '" + rankName + "' not found or cannot be deleted (e.g., last default rank).");
        }
    }

    private void handleSetPrefix(CommandSender sender, String[] args) {
        if (!sender.hasPermission("diamondplugin.rank.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank setprefix <name> <prefix>");
            return;
        }
        String rankName = args[1];
        String prefix = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        if (rankManager.setRankPrefix(rankName, prefix)) {
            sender.sendMessage(ChatColor.GREEN + "Prefix for rank '" + rankName + "' set to '" + prefix + ChatColor.GREEN + "'.");
        } else {
            sender.sendMessage(ChatColor.RED + "Rank '" + rankName + "' not found.");
        }
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("diamondplugin.rank.set")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank set <player> <name> [duration]");
            return;
        }
        String playerName = args[1];
        String rankName = args[2];
        String durationString = (args.length > 3) ? args[3] : "perm"; // Default to permanent

        long durationMillis = parseDuration(durationString);
        if (durationMillis == 0 && !durationString.equalsIgnoreCase("perm") && !durationString.equals("0")) {
            sender.sendMessage(ChatColor.RED + "Invalid duration format. Use 'perm' or units like s, m, h, d, mo (e.g., 30d, 1mo).");
            return;
        }


        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
        if (targetPlayer == null || (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline())) {
             sender.sendMessage(ChatColor.RED + "Player '" + playerName + "' not found.");
             return;
        }
        UUID targetUUID = targetPlayer.getUniqueId();

        if (rankManager.getRank(rankName) == null) {
            sender.sendMessage(ChatColor.RED + "Rank '" + rankName + "' does not exist.");
            return;
        }

        if (rankManager.setPlayerRank(targetUUID, rankName, durationMillis, true)) {
            String durationMessage = (durationMillis == -1) ? "permanently" : "for " + formatDuration(durationMillis);
            sender.sendMessage(ChatColor.GREEN + "Set rank for player '" + targetPlayer.getName() + "' to '" + rankName + "' " + durationMessage + ".");
             if (targetPlayer.isOnline()) {
                ((Player)targetPlayer).sendMessage(ChatColor.GREEN + "Your rank has been updated to " + rankName + " " + durationMessage + ".");
            }
        } else {
            // This case should ideally be caught by rank existence check above, but as a fallback:
            sender.sendMessage(ChatColor.RED + "Failed to set rank. Ensure rank exists and player data could be saved.");
        }
    }

    // Utility method to parse duration strings like "30d", "1h", "perm"
    private long parseDuration(String durationStr) {
        if (durationStr.equalsIgnoreCase("perm") || durationStr.equalsIgnoreCase("permanent")) {
            return -1; // Indicates permanent
        }
        if (durationStr.equals("0")) return -1; // Also treat "0" as permanent for simplicity, or could be invalid.

        long totalMillis = 0;
        String currentNumber = "";
        for (char c : durationStr.toCharArray()) {
            if (Character.isDigit(c)) {
                currentNumber += c;
            } else {
                if (currentNumber.isEmpty()) return 0; // Invalid format (e.g., "d30")
                long value = Long.parseLong(currentNumber);
                currentNumber = "";
                switch (Character.toLowerCase(c)) {
                    case 's': totalMillis += value * 1000; break;
                    case 'm': totalMillis += value * 60 * 1000; break;
                    case 'h': totalMillis += value * 60 * 60 * 1000; break;
                    case 'd': totalMillis += value * 24 * 60 * 60 * 1000; break;
                    case 'w': totalMillis += value * 7 * 24 * 60 * 60 * 1000; break;
                    case 'o': // Assuming 'mo' for month (approx 30 days)
                        if (durationStr.charAt(durationStr.indexOf(c) + 1) == 'o') {
                             totalMillis += value * 30L * 24 * 60 * 60 * 1000;
                             // Skip next 'o', but this simple parser might not handle "moo" well.
                             // A more robust parser or library would be better for complex cases.
                        } else {
                            return 0; // Invalid unit
                        }
                        break;
                    default: return 0; // Invalid unit
                }
            }
        }
        if (!currentNumber.isEmpty()) return 0; // Trailing numbers without unit

        return totalMillis == 0 ? 0 : totalMillis; // Return 0 if nothing was parsed, otherwise the total
    }

    // Utility method to format duration millis into a human-readable string
    private String formatDuration(long millis) {
        if (millis == -1) return "Permanent";
        if (millis < 1000) return millis + "ms";

        long days = TimeUnit.MILLISECONDS.toDays(millis);
        millis -= TimeUnit.DAYS.toMillis(days);
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        millis -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0) sb.append(seconds).append("s");

        String result = sb.toString().trim();
        return result.isEmpty() ? "0s" : result;
    }


    private void handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("diamondplugin.rank.set")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank remove <player>");
            return;
        }
        String playerName = args[1];
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);

        if (targetPlayer == null || (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline())) {
             sender.sendMessage(ChatColor.RED + "Player '" + playerName + "' not found.");
             return;
        }
        UUID targetUUID = targetPlayer.getUniqueId();

        if (rankManager.removePlayerRank(targetUUID)) {
            String defaultRankDisplay = rankManager.getDefaultRank() != null ? rankManager.getDefaultRank().getRankName() : "default";
            sender.sendMessage(ChatColor.GREEN + "Removed rank from player '" + targetPlayer.getName() + "'. They are now " + defaultRankDisplay + ".");
            if (targetPlayer.isOnline()) {
                ((Player)targetPlayer).sendMessage(ChatColor.YELLOW + "Your rank has been reset to " + defaultRankDisplay + ".");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Player '" + targetPlayer.getName() + "' did not have a specific rank set or could not be updated.");
        }
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Available Ranks:");
        for (Rank rank : rankManager.getAllRanks()) {
            sender.sendMessage(ChatColor.GRAY + "- " + rank.getRankName() + " (Prefix: " + ChatColor.translateAlternateColorCodes('&', rank.getPrefix()) + ChatColor.GRAY + ")");
        }
    }

    private void handleCheckExpiry(CommandSender sender, String[] args) {
        if (!sender.hasPermission("diamondplugin.rank.admin")) { // Or a new specific permission like diamondplugin.rank.checkexpiry
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank checkexpiry <player>");
            return;
        }
        String playerName = args[1];
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);

        if (targetPlayer == null || (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline())) {
            sender.sendMessage(ChatColor.RED + "Player '" + playerName + "' not found.");
            return;
        }

        RankManager.PlayerRankData playerData = rankManager.getPlayerRankData(targetPlayer.getUniqueId());
        Rank currentRank = rankManager.getRank(playerData.getRankName());

        if (currentRank == null) { // Should not happen if data is consistent
            sender.sendMessage(ChatColor.RED + "Player " + targetPlayer.getName() + " has an invalid rank assigned: " + playerData.getRankName());
            return;
        }

        sender.sendMessage(ChatColor.YELLOW + "Rank expiry information for " + targetPlayer.getName() + ":");
        sender.sendMessage(ChatColor.GRAY + "- Current Rank: " + currentRank.getRankName());
        if (playerData.isPermanent()) {
            sender.sendMessage(ChatColor.GRAY + "- Duration: Permanent");
        } else {
            long remainingMillis = playerData.getExpiresAt() - System.currentTimeMillis();
            if (remainingMillis <= 0) {
                sender.sendMessage(ChatColor.GRAY + "- Duration: Expired (should revert on next check or interaction)");
            } else {
                sender.sendMessage(ChatColor.GRAY + "- Time Remaining: " + formatDuration(remainingMillis));
                sender.sendMessage(ChatColor.GRAY + "- Expires At: " + new java.util.Date(playerData.getExpiresAt()).toString());
            }
        }
    }


    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("create", "delete", "setprefix", "set", "remove", "list", "checkexpiry");
            for (String sub : subCommands) {
                if (sub.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "delete":
                case "setprefix":
                case "set": // For the rank name in /rank set <player> <rank>
                    // For /rank delete <name> and /rank setprefix <name>
                    if (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("setprefix")) {
                        rankManager.getAllRanks().stream()
                                .map(Rank::getRankName)
                                .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                                .forEach(completions::add);
                    } else if (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("checkexpiry")) {
                        // For /rank set <player> ..., /rank remove <player>, /rank checkexpiry <player>
                        Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName)
                                .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                                .forEach(completions::add);
                    }
                    break;
                /* OLD set logic - now player name is arg[1]
                    if (args[0].equalsIgnoreCase("set")) break; // Handled in args.length == 3 for player names
                    rankManager.getAllRanks().stream()
                            .map(Rank::getRankName)
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .forEach(completions::add);
                    break;
                case "remove":
                    Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .forEach(completions::add);
                    break;
                */
            }
        } else if (args.length == 3) {
            // Tab completing rank name for /rank set <player> <rank> [duration]
            // Or prefix for /rank setprefix <rank> <prefix>
            if (args[0].equalsIgnoreCase("set")) {
                rankManager.getAllRanks().stream()
                        .map(Rank::getRankName)
                        .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                        .forEach(completions::add);
            }
            // No specific completions for prefix in setprefix, it's free text
        } else if (args.length == 4) {
            // Tab completing duration for /rank set <player> <rank> <duration>
            if (args[0].equalsIgnoreCase("set")) {
                List<String> durationHints = Arrays.asList("1h", "1d", "7d", "30d", "perm");
                for (String hint : durationHints) {
                    if (hint.toLowerCase().startsWith(args[3].toLowerCase())) {
                        completions.add(hint);
                    }
                }
            }
        }
        return completions.stream().sorted().collect(Collectors.toList());
    }
}
