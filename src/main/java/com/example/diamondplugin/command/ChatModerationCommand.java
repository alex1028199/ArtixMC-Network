package com.example.diamondplugin.command;

import com.example.diamondplugin.DiamondPlugin;
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

public class ChatModerationCommand implements CommandExecutor, TabCompleter {

    private final DiamondPlugin plugin;

    public ChatModerationCommand(DiamondPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        switch (command.getName().toLowerCase()) {
            case "mute":
                return handleMute(sender, args);
            case "unmute":
                return handleUnmute(sender, args);
            case "checkmute":
                return handleCheckMute(sender, args);
            case "clearchat":
                return handleClearChat(sender, args);
        }
        return false;
    }

    private boolean handleMute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("diamondplugin.chat.mute")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        // /mute <player> [duration] [reason...]
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /mute <player> [duration] [reason...]");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            sender.sendMessage(ChatColor.RED + "Player '" + args[0] + "' not found.");
            return true;
        }

        String durationString = "perm"; // Default to permanent
        int reasonStartIndex = 2;
        if (args.length >= 2) {
            // Try to parse args[1] as duration, if it fails, it's part of the reason
            long parsedDuration = parseDuration(args[1]);
            if (parsedDuration != 0 || args[1].equalsIgnoreCase("perm") || args[1].equals("0")) { // Successfully parsed as duration or is "perm"
                durationString = args[1];
            } else { // args[1] is part of the reason
                reasonStartIndex = 1;
            }
        }

        long durationMillis = parseDuration(durationString);
        if (durationMillis == 0 && !durationString.equalsIgnoreCase("perm") && !durationString.equals("0")) {
             sender.sendMessage(ChatColor.RED + "Invalid duration format: " + durationString + ". Use 'perm' or units like s, m, h, d, w, mo.");
             return true;
        }


        String reason = "Muted by an operator.";
        if (args.length > reasonStartIndex) {
            reason = String.join(" ", Arrays.copyOfRange(args, reasonStartIndex, args.length));
        }

        plugin.mutePlayer(target.getUniqueId(), reason, durationMillis, sender.getName());

        String durationMsg = (durationMillis == -1) ? "permanently" : "for " + formatDuration(durationMillis);
        String broadcastMsg = ChatColor.YELLOW + target.getName() + " has been muted by " + sender.getName() + " " + durationMsg + ". Reason: " + reason;

        // Broadcast to staff or globally (configurable later, for now staff)
        Bukkit.getOnlinePlayers().stream()
            .filter(p -> p.hasPermission("diamondplugin.chat.mute")) // Notify those who can mute
            .forEach(p -> p.sendMessage(broadcastMsg));
        if (!(sender instanceof Player && ((Player)sender).hasPermission("diamondplugin.chat.mute"))) {
             sender.sendMessage(broadcastMsg); // Send to console if console muted or sender is not staff
        }


        if (target.isOnline()) {
            ((Player) target).sendMessage(ChatColor.RED + "You have been muted by " + sender.getName() + " " + durationMsg + ". Reason: " + reason);
        }
        return true;
    }

    private boolean handleUnmute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("diamondplugin.chat.unmute")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /unmute <player>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
         // Check if player exists and has played before, or if they are online,
         // or if we have mute data for them (they might be an offline player who was muted)
        if (target == null || ((!target.hasPlayedBefore() && !target.isOnline()) && plugin.getMuteData(target.getUniqueId()) == null )) {
            sender.sendMessage(ChatColor.RED + "Player '" + args[0] + "' not found or no mute data exists for them.");
            return true;
        }

        if (plugin.unmutePlayer(target.getUniqueId(), sender.getName())) {
            String broadcastMsg = ChatColor.GREEN + target.getName() + " has been unmuted by " + sender.getName() + ".";
            Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("diamondplugin.chat.unmute"))
                .forEach(p -> p.sendMessage(broadcastMsg));
            if (!(sender instanceof Player && ((Player)sender).hasPermission("diamondplugin.chat.unmute"))) {
                 sender.sendMessage(broadcastMsg);
            }

            if (target.isOnline()) {
                ((Player) target).sendMessage(ChatColor.GREEN + "You have been unmuted by " + sender.getName() + ".");
            }
        } else {
            sender.sendMessage(ChatColor.RED + target.getName() + " is not currently muted.");
        }
        return true;
    }

    private boolean handleCheckMute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("diamondplugin.chat.checkmute")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /checkmute <player>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target == null || ((!target.hasPlayedBefore() && !target.isOnline()) && plugin.getMuteData(target.getUniqueId()) == null )) {
             sender.sendMessage(ChatColor.RED + "Player '" + args[0] + "' not found or no mute record exists.");
             return true;
        }

        DiamondPlugin.MuteData muteData = plugin.getMuteData(target.getUniqueId());
        if (muteData == null || muteData.hasExpired()) { // Also check hasExpired in case the scheduled task hasn't run yet
            plugin.unmutePlayer(target.getUniqueId(), "System (Expired Check)"); // Clean up if expired
            sender.sendMessage(ChatColor.GREEN + target.getName() + " is not muted.");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Mute status for " + target.getName() + ":");
        sender.sendMessage(ChatColor.GRAY + "- Reason: " + muteData.reason);
        sender.sendMessage(ChatColor.GRAY + "- Muted by: " + muteData.muterName);
        if (muteData.isPermanent()) {
            sender.sendMessage(ChatColor.GRAY + "- Expires: Permanent");
        } else {
            sender.sendMessage(ChatColor.GRAY + "- Expires: " + formatDuration(muteData.expiresAt - System.currentTimeMillis()));
            sender.sendMessage(ChatColor.GRAY + "  (At: " + new java.util.Date(muteData.expiresAt).toString() + ")");
        }
        return true;
    }


    private boolean handleClearChat(CommandSender sender, String[] args) {
        String clearType = "global";
        if (args.length > 0 && args[0].equalsIgnoreCase("self")) {
            clearType = "self";
        }

        if (clearType.equals("global")) {
            if (!sender.hasPermission("diamondplugin.chat.clearchat.global")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to clear global chat.");
                return true;
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.hasPermission("diamondplugin.chat.clearchat.bypass")) {
                    for (int i = 0; i < 150; i++) {
                        player.sendMessage("");
                    }
                }
            }
            Bukkit.broadcastMessage(ChatColor.GOLD + "Chat has been cleared by " + sender.getName() + ".");
        } else { // self
            if (!sender.hasPermission("diamondplugin.chat.clearchat.self")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to clear your own chat.");
                return true;
            }
            if (sender instanceof Player) {
                Player player = (Player) sender;
                for (int i = 0; i < 150; i++) {
                    player.sendMessage("");
                }
                player.sendMessage(ChatColor.GREEN + "Your chat has been cleared.");
            } else {
                sender.sendMessage("Console chat cannot be cleared in this way.");
            }
        }
        return true;
    }

    // Duration parsing and formatting (similar to RankCommand, consider utility class)
    private long parseDuration(String durationStr) {
        if (durationStr.equalsIgnoreCase("perm") || durationStr.equalsIgnoreCase("permanent")) return -1L;
        if (durationStr.equals("0")) return -1L;

        long totalMillis = 0;
        String currentNumber = "";
        for (char c : durationStr.toCharArray()) {
            if (Character.isDigit(c)) {
                currentNumber += c;
            } else {
                if (currentNumber.isEmpty()) return 0;
                long value = Long.parseLong(currentNumber);
                currentNumber = "";
                switch (Character.toLowerCase(c)) {
                    case 's': totalMillis += TimeUnit.SECONDS.toMillis(value); break;
                    case 'm': totalMillis += TimeUnit.MINUTES.toMillis(value); break;
                    case 'h': totalMillis += TimeUnit.HOURS.toMillis(value); break;
                    case 'd': totalMillis += TimeUnit.DAYS.toMillis(value); break;
                    case 'w': totalMillis += TimeUnit.DAYS.toMillis(value * 7); break;
                    case 'o': // month
                        if (durationStr.length() > durationStr.indexOf(c) + 1 && durationStr.charAt(durationStr.indexOf(c) + 1) == 'o') {
                             totalMillis += TimeUnit.DAYS.toMillis(value * 30); // Approx
                        } else return 0;
                        break;
                    default: return 0;
                }
            }
        }
         if (!currentNumber.isEmpty()) return 0; // Trailing numbers without unit
        return totalMillis == 0 ? 0 : totalMillis;
    }

    private String formatDuration(long millis) {
        if (millis == -1) return "Permanent";
        if (millis < 0) return "now (error)";
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
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append("s"); // show 0s if everything else is 0

        String result = sb.toString().trim();
        return result.isEmpty() ? "<1s" : result;
    }


    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        String cmdName = command.getName().toLowerCase();

        if (cmdName.equals("mute") || cmdName.equals("unmute") || cmdName.equals("checkmute")) {
            if (args.length == 1) { // Player name
                Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                        .forEach(completions::add);
            } else if (cmdName.equals("mute") && args.length == 2) { // Duration for /mute
                 List<String> durationHints = Arrays.asList("10m", "1h", "1d", "perm");
                 durationHints.stream()
                         .filter(hint -> hint.toLowerCase().startsWith(args[1].toLowerCase()))
                         .forEach(completions::add);
            }
        } else if (cmdName.equals("clearchat")) {
            if (args.length == 1) {
                if ("global".startsWith(args[0].toLowerCase())) completions.add("global");
                if ("self".startsWith(args[0].toLowerCase())) completions.add("self");
            }
        }
        return completions.stream().sorted().collect(Collectors.toList());
    }
}
