package com.example.diamondplugin.command;

import com.example.diamondplugin.DiamondPlugin;
import com.example.diamondplugin.config.ChatConfig;
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
import java.util.List;
import java.util.stream.Collectors;

public class ChannelCommand implements CommandExecutor, TabCompleter {

    private final DiamondPlugin plugin;
    private final ChatConfig chatConfig;

    public ChannelCommand(DiamondPlugin plugin) {
        this.plugin = plugin;
        this.chatConfig = plugin.getChatConfig();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        // Handling /channel, /lc, /sc, /gc
        switch (command.getName().toLowerCase()) {
            case "channel":
                return handleChannelSwitch(player, args);
            case "localchat":
            case "lc":
                return handleQuickChannelMessage(player, "local", args);
            case "staffchat":
            case "sc":
                return handleQuickChannelMessage(player, "staff", args);
            case "globalchat":
            case "gc":
                return handleQuickChannelMessage(player, "global", args);
        }
        return false;
    }

    private boolean handleChannelSwitch(Player player, String[] args) {
        if (!player.hasPermission("diamondplugin.chat.switchchannel")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to switch channels.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Usage: /channel <local|global|staff>");
            player.sendMessage(ChatColor.YELLOW + "You are currently in: " + plugin.getPlayerChannel(player.getUniqueId()) + " chat.");
            return true;
        }

        String targetChannel = args[0].toLowerCase();
        switch (targetChannel) {
            case "local":
                if (!chatConfig.isLocalChannelEnabled()) {
                    player.sendMessage(ChatColor.RED + "Local chat is currently disabled.");
                    return true;
                }
                plugin.setPlayerChannel(player.getUniqueId(), "local");
                player.sendMessage(ChatColor.GREEN + "Switched to Local chat.");
                break;
            case "staff":
                if (!chatConfig.isStaffChannelEnabled()) {
                    player.sendMessage(ChatColor.RED + "Staff chat is currently disabled.");
                    return true;
                }
                if (!player.hasPermission(chatConfig.getStaffChannelPermission())) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to join Staff chat.");
                    return true;
                }
                plugin.setPlayerChannel(player.getUniqueId(), "staff");
                player.sendMessage(ChatColor.GREEN + "Switched to Staff chat.");
                break;
            case "global":
                plugin.setPlayerChannel(player.getUniqueId(), "global");
                player.sendMessage(ChatColor.GREEN + "Switched to Global chat.");
                break;
            default:
                player.sendMessage(ChatColor.RED + "Unknown channel. Usage: /channel <local|global|staff>");
                break;
        }
        return true;
    }

    private boolean handleQuickChannelMessage(Player player, String channel, String[] args) {
        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Usage: /" + channel.charAt(0) + "c <message>");
            return true;
        }

        String message = String.join(" ", args);
        String originalChannel = plugin.getPlayerChannel(player.getUniqueId());

        // Temporarily switch channel, send message, then switch back
        if (channel.equals("staff") && !player.hasPermission(chatConfig.getStaffChannelPermission())) {
             player.sendMessage(ChatColor.RED + "You don't have permission to use staff chat.");
             return true;
        }
         if (channel.equals("staff") && !player.hasPermission("diamondplugin.chat.staffchannel.send")) { // Specific send permission
            player.sendMessage(ChatColor.RED + "You don't have permission to send messages in staff chat directly.");
            return true;
        }


        plugin.setPlayerChannel(player.getUniqueId(), channel);
        player.chat(message); // This will trigger the ChatListener
        plugin.setPlayerChannel(player.getUniqueId(), originalChannel); // Revert to original channel

        return true;
    }


    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("channel")) {
            if (args.length == 1) {
                List<String> channels = new ArrayList<>();
                channels.add("global"); // Global is always an option
                if (chatConfig.isLocalChannelEnabled()) channels.add("local");
                if (chatConfig.isStaffChannelEnabled() && sender.hasPermission(chatConfig.getStaffChannelPermission())) {
                    channels.add("staff");
                }
                for (String ch : channels) {
                    if (ch.toLowerCase().startsWith(args[0].toLowerCase())) {
                        completions.add(ch);
                    }
                }
            }
        }
        // No tab completion for /lc, /sc, /gc message content
        return completions.stream().sorted().collect(Collectors.toList());
    }
}
