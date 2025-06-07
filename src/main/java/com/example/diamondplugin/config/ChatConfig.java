package com.example.diamondplugin.config;

import com.example.diamondplugin.DiamondPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public class ChatConfig {

    private final DiamondPlugin plugin;
    private String chatFormat;
    private String defaultPrefixColor;
    private boolean hoverTextEnabled;
    private List<String> hoverTextLines;

    // Channel settings
    private boolean channelsEnabled;
    private String defaultChannel;
    private boolean localChannelEnabled;
    private String localChannelPrefix;
    private int localChannelRange;
    private String localChannelCommand;
    private boolean staffChannelEnabled;
    private String staffChannelPrefix;
    private String staffChannelPermission;
    private String staffChannelCommand;
    private String globalChannelCommand;


    public ChatConfig(DiamondPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        FileConfiguration config = plugin.getConfig();

        // Add defaults to config.yml if not already present
        config.addDefault("chat.format", "{prefix}{player_name}&f: {message}");
        config.addDefault("chat.default-prefix-color", "&7");
        config.addDefault("chat.hover-text.enabled", true);
        config.addDefault("chat.hover-text.lines", List.of(
                "&bRank: &f{rank_name}",
                "&bBalance: &f${balance}", // Placeholder
                "&bPlaytime: &f{playtime}" // Placeholder
        ));

        // Channel defaults
        config.addDefault("chat.channels.enabled", true);
        config.addDefault("chat.channels.default-channel", "global");
        config.addDefault("chat.channels.local.enabled", true);
        config.addDefault("chat.channels.local.prefix", "&7[L] ");
        config.addDefault("chat.channels.local.range", 100);
        config.addDefault("chat.channels.local.command", "/localchat");
        config.addDefault("chat.channels.staff.enabled", true);
        config.addDefault("chat.channels.staff.prefix", "&c[S] ");
        config.addDefault("chat.channels.staff.permission", "diamondplugin.chat.staffchannel");
        config.addDefault("chat.channels.staff.command", "/staffchat");
        config.addDefault("chat.channels.global-command", "/globalchat");


        config.options().copyDefaults(true);
        plugin.saveConfig(); // Save the config if defaults were added

        // Load values
        chatFormat = config.getString("chat.format", "{prefix}{player_name}&f: {message}");
        defaultPrefixColor = config.getString("chat.default-prefix-color", "&7");
        hoverTextEnabled = config.getBoolean("chat.hover-text.enabled", true);
        hoverTextLines = config.getStringList("chat.hover-text.lines");

        // Load channel values
        channelsEnabled = config.getBoolean("chat.channels.enabled", true);
        defaultChannel = config.getString("chat.channels.default-channel", "global").toLowerCase();
        localChannelEnabled = config.getBoolean("chat.channels.local.enabled", true);
        localChannelPrefix = config.getString("chat.channels.local.prefix", "&7[L] ");
        localChannelRange = config.getInt("chat.channels.local.range", 100);
        localChannelCommand = config.getString("chat.channels.local.command", "/localchat");
        staffChannelEnabled = config.getBoolean("chat.channels.staff.enabled", true);
        staffChannelPrefix = config.getString("chat.channels.staff.prefix", "&c[S] ");
        staffChannelPermission = config.getString("chat.channels.staff.permission", "diamondplugin.chat.staffchannel");
        staffChannelCommand = config.getString("chat.channels.staff.command", "/staffchat");
        globalChannelCommand = config.getString("chat.channels.global-command", "/globalchat");
    }

    public String getChatFormat() {
        return chatFormat;
    }

    public String getDefaultPrefixColor() {
        return defaultPrefixColor;
    }

    public boolean isHoverTextEnabled() {
        return hoverTextEnabled;
    }

    public List<String> getHoverTextLines() {
        return hoverTextLines;
    }

    // Channel config getters
    public boolean isChannelsEnabled() {
        return channelsEnabled;
    }

    public String getDefaultChannel() {
        return defaultChannel;
    }

    public boolean isLocalChannelEnabled() {
        return localChannelEnabled;
    }

    public String getLocalChannelPrefix() {
        return localChannelPrefix;
    }

    public int getLocalChannelRange() {
        return localChannelRange;
    }

    public String getLocalChannelCommand() {
        return localChannelCommand;
    }

    public boolean isStaffChannelEnabled() {
        return staffChannelEnabled;
    }

    public String getStaffChannelPrefix() {
        return staffChannelPrefix;
    }

    public String getStaffChannelPermission() {
        return staffChannelPermission;
    }

    public String getStaffChannelCommand() {
        return staffChannelCommand;
    }

    public String getGlobalChannelCommand() {
        return globalChannelCommand;
    }
}
