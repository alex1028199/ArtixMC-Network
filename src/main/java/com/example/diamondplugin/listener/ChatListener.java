package com.example.diamondplugin.listener;

import com.example.diamondplugin.DiamondPlugin;
import com.example.diamondplugin.config.ChatConfig;
import com.example.diamondplugin.rank.Rank;
import com.example.diamondplugin.rank.RankManager;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.List;
import java.util.stream.Collectors;

public class ChatListener implements Listener {

    private final DiamondPlugin plugin;
    private final RankManager rankManager;
    private final ChatConfig chatConfig;

    public ChatListener(DiamondPlugin plugin) {
        this.plugin = plugin;
        this.rankManager = plugin.getRankManager();
        this.chatConfig = plugin.getChatConfig();
    }

    @EventHandler(priority = EventPriority.HIGHEST) // Use HIGHEST to ensure our formatting is applied last, or as desired
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // Check if player is muted
        if (plugin.isPlayerMuted(player.getUniqueId())) {
            DiamondPlugin.MuteData muteData = plugin.getMuteData(player.getUniqueId());
            String reason = muteData.reason;
            String expiryMessage;
            if (muteData.isPermanent()) {
                expiryMessage = "permanently";
            } else {
                long remainingMillis = muteData.expiresAt - System.currentTimeMillis();
                expiryMessage = "for " + formatDuration(remainingMillis); // You'll need a formatDuration method here or in a util class
            }
            player.sendMessage(ChatColor.RED + "You are currently muted.");
            player.sendMessage(ChatColor.RED + "Reason: " + reason);
            player.sendMessage(ChatColor.RED + "Expires: " + expiryMessage);
            event.setCancelled(true);
            return;
        }

        String playerCurrentChannel = plugin.getPlayerChannel(player.getUniqueId());

        // If channels are not enabled, or for some reason player channel is null, fallback to default behavior
        if (!chatConfig.isChannelsEnabled() || playerCurrentChannel == null) {
            formatChatMessage(event, player, "", ""); // No channel prefix
            return;
        }

        switch (playerCurrentChannel) {
            case "local":
                if (chatConfig.isLocalChannelEnabled()) {
                    handleLocalChat(event, player, playerCurrentChannel);
                } else { // Fallback if local chat is somehow disabled but player is in it
                    formatChatMessage(event, player, "", playerCurrentChannel);
                }
                break;
            case "staff":
                if (chatConfig.isStaffChannelEnabled()) {
                    handleStaffChat(event, player, playerCurrentChannel);
                } else { // Fallback
                    formatChatMessage(event, player, "", playerCurrentChannel);
                }
                break;
            case "global":
            default:
                 // Global may or may not have a prefix, handle consistently
                formatChatMessage(event, player, "", playerCurrentChannel); // No extra prefix for global, or define one if desired
                break;
        }
    }

    private void handleLocalChat(AsyncPlayerChatEvent event, Player player, String currentChannel) {
        event.getRecipients().clear();
        event.getRecipients().add(player); // Player sees their own message
        // Add console
        Bukkit.getConsoleSender().sendMessage(
            ChatColor.translateAlternateColorCodes('&', chatConfig.getLocalChannelPrefix()) +
            player.getDisplayName() + ": " + event.getMessage()
        );


        int localRangeSquared = chatConfig.getLocalChannelRange() * chatConfig.getLocalChannelRange();
        for (Player recipient : Bukkit.getOnlinePlayers()) {
            if (recipient.getWorld().equals(player.getWorld()) &&
                recipient.getLocation().distanceSquared(player.getLocation()) <= localRangeSquared) {
                if (!event.getRecipients().contains(recipient)) {
                    event.getRecipients().add(recipient);
                }
            } else if (recipient.hasPermission("diamondplugin.chat.spyLocal")) {
                 if (!event.getRecipients().contains(recipient)) { // Avoid sending to self if they also have spy perm
                    recipient.sendMessage(ChatColor.GRAY + "[Spy][Local] " + player.getName() + ": " + event.getMessage());
                    // They see it via spy, not as a direct recipient of the formatted local message
                }
            }
        }
        formatChatMessage(event, player, chatConfig.getLocalChannelPrefix(), currentChannel);
    }

    // Helper to format duration for mute message (can be moved to a utility class)
    private String formatDuration(long millis) {
        if (millis < 0) return "now (error)"; // Should not happen if logic is correct
        if (millis < 1000) return millis + "ms";

        long days = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(millis);
        millis -= java.util.concurrent.TimeUnit.DAYS.toMillis(days);
        long hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(millis);
        millis -= java.util.concurrent.TimeUnit.HOURS.toMillis(hours);
        long minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(millis);
        millis -= java.util.concurrent.TimeUnit.MINUTES.toMillis(minutes);
        long seconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(millis);

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0) sb.append(seconds).append("s");

        String result = sb.toString().trim();
        return result.isEmpty() ? "<1s" : result;
    }


    private void handleStaffChat(AsyncPlayerChatEvent event, Player player, String currentChannel) {
        if (!player.hasPermission(chatConfig.getStaffChannelPermission())) {
            player.sendMessage(ChatColor.RED + "You do not have permission to speak in staff chat.");
            plugin.setPlayerChannel(player.getUniqueId(), chatConfig.getDefaultChannel()); // Move them to default
            event.setCancelled(true);
            return;
        }
        event.getRecipients().clear();
        event.getRecipients().add(player); // Player sees their own message
         // Add console
        Bukkit.getConsoleSender().sendMessage(
            ChatColor.translateAlternateColorCodes('&', chatConfig.getStaffChannelPrefix()) +
            player.getDisplayName() + ": " + event.getMessage()
        );


        for (Player recipient : Bukkit.getOnlinePlayers()) {
            if (recipient.hasPermission(chatConfig.getStaffChannelPermission())) {
                 if (!event.getRecipients().contains(recipient)) {
                    event.getRecipients().add(recipient);
                }
            }
        }
        formatChatMessage(event, player, chatConfig.getStaffChannelPrefix(), currentChannel);
    }


    // Consolidated message formatting logic
    private void formatChatMessage(AsyncPlayerChatEvent event, Player player, String channelPrefix, String channelNameForHover) {
        Rank playerRank = rankManager.getPlayerRank(player.getUniqueId());
        String rankName = playerRank != null ? playerRank.getRankName() : rankManager.getDefaultRank().getRankName();
        String rankPrefix = playerRank != null ? playerRank.getPrefix() : rankManager.getDefaultRank().getPrefix();

        if (rankPrefix == null || rankPrefix.trim().isEmpty()) {
            rankPrefix = chatConfig.getDefaultPrefixColor();
        }
        rankPrefix = ChatColor.translateAlternateColorCodes('&', rankPrefix);
        channelPrefix = ChatColor.translateAlternateColorCodes('&', channelPrefix);

        String fullPrefix = channelPrefix + rankPrefix; // Combine channel and rank prefix

        String messageFormat = chatConfig.getChatFormat();
        // Basic placeholders
        messageFormat = messageFormat.replace("{player_name}", player.getDisplayName());
        messageFormat = messageFormat.replace("{world}", player.getWorld().getName());

        if (chatConfig.isHoverTextEnabled() && !chatConfig.getHoverTextLines().isEmpty()) {
            event.setCancelled(true);

            TextComponent prefixComponent = new TextComponent(fullPrefix);

            List<String> hoverLines = chatConfig.getHoverTextLines().stream()
                    .map(line -> line.replace("{rank_name}", rankName)) // Use actual rank name for hover
                    .map(line -> line.replace("{channel_name}", channelNameForHover)) // Add channel info
                    .map(line -> line.replace("{balance}", "N/A")) // Placeholder
                    .map(line -> line.replace("{playtime}", "N/A")) // Placeholder
                    .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                    .collect(Collectors.toList());

            ComponentBuilder hoverBuilder = new ComponentBuilder();
            for (int i = 0; i < hoverLines.size(); i++) {
                hoverBuilder.append(new TextComponent(hoverLines.get(i))); // bungee TextComponent
                if (i < hoverLines.size() - 1) {
                    hoverBuilder.append("\n");
                }
            }
            prefixComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverBuilder.create())); // bungee HoverEvent & create()

            String finalMessageFormat = chatConfig.getChatFormat();
            TextComponent messageToSend = new TextComponent(); // This will be the parent component

            // Logic to replace {prefix} placeholder with the prefixComponent
            // and other parts with simple TextComponents
            String[] parts = finalMessageFormat.split("(?=\\{prefix\\}|\\{player_name\\}|\\{message\\})|(?<=\\{prefix\\}|\\{player_name\\}|\\{message\\})");

            for(String part : parts) {
                if(part.equals("{prefix}")) {
                    messageToSend.addExtra(prefixComponent);
                } else if (part.equals("{player_name}")) {
                    messageToSend.addExtra(new TextComponent(ChatColor.translateAlternateColorCodes('&', player.getDisplayName())));
                } else if (part.equals("{message}")) {
                    messageToSend.addExtra(new TextComponent(ChatColor.translateAlternateColorCodes('&', event.getMessage())));
                } else {
                    messageToSend.addExtra(new TextComponent(ChatColor.translateAlternateColorCodes('&', part)));
                }
            }

            // Send to all recipients
            //Bukkit.getServer().spigot().broadcast(messageToSend); //This sends to everyone on server
             for (Player recipient : event.getRecipients()) {
                 recipient.spigot().sendMessage(messageToSend);
             }
            plugin.getLogger().info("[Chat] Player " + player.getName() + " (Channel: " + channelNameForHover + "): " + event.getMessage());
            plugin.getLogger().info("[Chat-Formatted] " + TextComponent.toLegacyText(messageToSend));


        } else {
            // Standard formatting without hover
            String formattedMessage = messageFormat.replace("{prefix}", fullPrefix); // Use full prefix
            formattedMessage = formattedMessage.replace("{message}", event.getMessage());
            // player_name and world already replaced
            event.setFormat(ChatColor.translateAlternateColorCodes('&', formattedMessage).replace("%", "%%"));
        }
    }
}
