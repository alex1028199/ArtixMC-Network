package com.example.diamondplugin;

import com.example.diamondplugin.config.ChatConfig;
import com.example.diamondplugin.config.EconomyConfig;
import com.example.diamondplugin.claim.ClaimManager; // Added
import com.example.diamondplugin.config.ChatConfig;
import com.example.diamondplugin.config.ClaimConfig;
import com.example.diamondplugin.config.EconomyConfig;
import com.example.diamondplugin.config.TeleportConfig;
import com.example.diamondplugin.economy.EconomyManager;
import com.example.diamondplugin.permission.VaultManager;
import com.example.diamondplugin.rank.RankManager;
import com.example.diamondplugin.shop.ShopManager;
import com.example.diamondplugin.teleport.TeleportManager;
import com.example.diamondplugin.teleport.TeleportRequest;
import com.example.diamondplugin.rank.Rank;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class DiamondPlugin extends JavaPlugin {

    private FileConfiguration ranksConfig = null;
    private File ranksConfigFile = null;
    private FileConfiguration playerDataConfig = null;
    private File playerDataConfigFile = null;
    private File chatDataFile = null;
    private FileConfiguration chatDataConfig = null;
    private File homesFile = null;
    private FileConfiguration homesConfig = null;
    private File shopsFile = null;
    private FileConfiguration shopsConfig = null;
    private File claimsFile = null; // Added
    private FileConfiguration claimsConfig = null; // Added

    private RankManager rankManager;
    private VaultManager vaultManager;
    private ChatConfig chatConfig;
    private TeleportConfig teleportConfig;
    private EconomyConfig economyConfig;
    private ClaimConfig claimConfig;
    private TeleportManager teleportManager;
    private EconomyManager economyManager;
    private ShopManager shopManager;
    private ClaimManager claimManager; // Added

    private com.example.diamondplugin.command.HomeCommands homeCommandsExecutor;

    private final Map<UUID, String> playerChannels = new HashMap<>();
    private final Map<UUID, MuteData> mutedPlayers = new HashMap<>();
    private final Map<UUID, TeleportRequest> activeTeleportRequestsToTarget = new HashMap<>();
    private final Map<UUID, TeleportRequest> activeTeleportRequestsFromRequester = new HashMap<>();

    public static class MuteData {
        public final long expiresAt;
        public final String reason;
        public final String muterName;

        public MuteData(long expiresAt, String reason, String muterName) {
            this.expiresAt = expiresAt;
            this.reason = reason;
            this.muterName = muterName;
        }
        public boolean isPermanent() { return expiresAt == -1; }
        public boolean hasExpired() {
            if (isPermanent()) return false;
            return System.currentTimeMillis() > expiresAt;
        }
    }

    @Override
    public void onEnable() {
        getLogger().info("[DiamondPlugin] enabled!");

        // Initialize Configs first
        this.chatConfig = new ChatConfig(this);
        this.teleportConfig = new TeleportConfig(this);
        this.economyConfig = new EconomyConfig(this);
        getConfig().options().copyDefaults(true);
        saveConfig();

        loadConfiguration(); // Main config.yml
        setupRankConfig();   // ranks.yml
        setupPlayerDataConfig(); // playerdata.yml
        setupChatDataConfig();   // chatdata.yml
        setupHomesConfig();      // homes.yml
        setupShopsConfig();      // shops.yml
        setupClaimsConfig();     // Added: claims.yml

        // Initialize Managers
        this.vaultManager = new VaultManager(this);
        this.rankManager = new RankManager(this);
        this.teleportManager = new TeleportManager(this);
        this.economyManager = new EconomyManager(this);
        this.shopManager = new ShopManager(this);
        this.claimManager = new ClaimManager(this); // Added

        // Register Vault Economy
        if (vaultManager.isVaultAvailable()) {
            getServer().getServicesManager().register(Economy.class, new com.example.diamondplugin.economy.Economy_Diamond(this), this, ServicePriority.Normal);
            getLogger().info("Successfully registered DiamondPlugin Economy with Vault.");
        } else {
            getLogger().warning("Vault not found. DiamondPlugin Economy integration disabled.");
        }

        // Register Commands
        this.getCommand("diamondplugin").setExecutor(new DiamondPluginCommand());
        com.example.diamondplugin.command.RankCommand rankCommandExecutor = new com.example.diamondplugin.command.RankCommand(this);
        this.getCommand("rank").setExecutor(rankCommandExecutor);
        com.example.diamondplugin.command.ChannelCommand channelCommandExecutor = new com.example.diamondplugin.command.ChannelCommand(this);
        this.getCommand("channel").setExecutor(channelCommandExecutor);
        this.getCommand("localchat").setExecutor(channelCommandExecutor);
        this.getCommand("staffchat").setExecutor(channelCommandExecutor);
        this.getCommand("globalchat").setExecutor(channelCommandExecutor);
        com.example.diamondplugin.command.ChatModerationCommand chatModExecutor = new com.example.diamondplugin.command.ChatModerationCommand(this);
        this.getCommand("mute").setExecutor(chatModExecutor);
        this.getCommand("unmute").setExecutor(chatModExecutor);
        this.getCommand("checkmute").setExecutor(chatModExecutor);
        this.getCommand("clearchat").setExecutor(chatModExecutor);
        this.homeCommandsExecutor = new com.example.diamondplugin.command.HomeCommands(this);
        this.getCommand("sethome").setExecutor(homeCommandsExecutor);
        this.getCommand("home").setExecutor(homeCommandsExecutor);
        this.getCommand("delhome").setExecutor(homeCommandsExecutor);
        this.getCommand("homes").setExecutor(homeCommandsExecutor);
        com.example.diamondplugin.command.SpawnCommands spawnCommandsExecutor = new com.example.diamondplugin.command.SpawnCommands(this, this.homeCommandsExecutor);
        this.getCommand("setspawn").setExecutor(spawnCommandsExecutor);
        this.getCommand("spawn").setExecutor(spawnCommandsExecutor);
        com.example.diamondplugin.command.TpaCommands tpaCommandsExecutor = new com.example.diamondplugin.command.TpaCommands(this, this.homeCommandsExecutor);
        this.getCommand("tpa").setExecutor(tpaCommandsExecutor);
        this.getCommand("tpahere").setExecutor(tpaCommandsExecutor);
        this.getCommand("tpaccept").setExecutor(tpaCommandsExecutor);
        this.getCommand("tpdeny").setExecutor(tpaCommandsExecutor);
        this.getCommand("tpcancel").setExecutor(tpaCommandsExecutor);

        com.example.diamondplugin.command.EconomyCommands economyCommandsExecutor = new com.example.diamondplugin.command.EconomyCommands(this); // This was already added in previous subtask, ensure it's here
        this.getCommand("balance").setExecutor(economyCommandsExecutor);
        this.getCommand("pay").setExecutor(economyCommandsExecutor);
        this.getCommand("eco").setExecutor(economyCommandsExecutor);

        com.example.diamondplugin.command.ShopCommand shopCommandExecutor = new com.example.diamondplugin.command.ShopCommand(this);
        this.getCommand("shop").setExecutor(shopCommandExecutor);

        com.example.diamondplugin.command.BankCommands bankCommandsExecutor = new com.example.diamondplugin.command.BankCommands(this);
        this.getCommand("bank").setExecutor(bankCommandsExecutor);

        com.example.diamondplugin.command.ClaimCommands claimCommandsExecutor = new com.example.diamondplugin.command.ClaimCommands(this);
        this.getCommand("claim").setExecutor(claimCommandsExecutor);
        this.getCommand("unclaim").setExecutor(claimCommandsExecutor);
        this.getCommand("abandonclaim").setExecutor(claimCommandsExecutor);
        this.getCommand("claimslist").setExecutor(claimCommandsExecutor);
        this.getCommand("myclaims").setExecutor(claimCommandsExecutor);


        // Register Listeners
        getServer().getPluginManager().registerEvents(new com.example.diamondplugin.listener.ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new com.example.diamondplugin.listener.PlayerMoveListener_Teleport(this), this);
        getServer().getPluginManager().registerEvents(new com.example.diamondplugin.listener.PlayerJoinListener_Economy(this), this);
        getServer().getPluginManager().registerEvents(new com.example.diamondplugin.listener.ShopListener(this), this);
        getServer().getPluginManager().registerEvents(new com.example.diamondplugin.listener.ShopListener(this), this);
        getServer().getPluginManager().registerEvents(new com.example.diamondplugin.listener.ClaimToolListener(this), this); // This was added in previous subtask, ensure it's here
        getServer().getPluginManager().registerEvents(new com.example.diamondplugin.listener.ClaimProtectionListener(this), this); // Added ClaimProtectionListener

        startPeriodicTasks();
    }

    @Override
    public void onDisable() {
        if (rankManager != null) rankManager.savePlayerData();
        if (economyManager != null) economyManager.saveBalances();
        saveRankConfig();
        saveChatDataConfig();
        saveHomesConfig();
        saveShopsConfig();
        saveShopsConfig();
        saveClaimsConfig();
        if(claimManager != null) claimManager.saveClaims(); // Added
        Bukkit.getScheduler().cancelTasks(this);
        getLogger().info("[DiamondPlugin] disabled!");
    }

    private void loadConfiguration() {
        saveDefaultConfig();
        String welcomeMessage = getConfig().getString("welcome-message");
        if (welcomeMessage != null) {
            getLogger().info(welcomeMessage);
        }
    }

    // Config Getters & Savers
    public FileConfiguration getRankConfig() { if (ranksConfig == null) reloadRankConfig(); return ranksConfig; }
    public void reloadRankConfig() {
        if (ranksConfigFile == null) ranksConfigFile = new File(getDataFolder(), "ranks.yml");
        if (!ranksConfigFile.exists()) saveResource("ranks.yml", false);
        ranksConfig = YamlConfiguration.loadConfiguration(ranksConfigFile);
        InputStream defaultStream = getResource("ranks.yml");
        if (defaultStream != null) ranksConfig.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream)));
    }
    public void saveRankConfig() { if (ranksConfig == null || ranksConfigFile == null) return; try { getRankConfig().save(ranksConfigFile); } catch (IOException ex) { getLogger().log(Level.SEVERE, "Could not save ranks.yml", ex); } }
    private void setupRankConfig() {
        ranksConfigFile = new File(getDataFolder(), "ranks.yml");
        if (!ranksConfigFile.exists()) saveResource("ranks.yml", false);
        ranksConfig = YamlConfiguration.loadConfiguration(ranksConfigFile);
    }

    public FileConfiguration getPlayerDataConfig() { if (playerDataConfig == null) reloadPlayerDataConfig(); return playerDataConfig; }
    public void reloadPlayerDataConfig() {
        if (playerDataConfigFile == null) playerDataConfigFile = new File(getDataFolder(), "playerdata.yml");
        if (!playerDataConfigFile.exists()) {
            saveResource("playerdata.yml", false);
            FileConfiguration newCfg = YamlConfiguration.loadConfiguration(playerDataConfigFile);
            if (!newCfg.isConfigurationSection("players")) { newCfg.createSection("players"); try { newCfg.save(playerDataConfigFile); } catch (IOException e) { getLogger().log(Level.SEVERE, "Could not save initial playerdata.yml with players section", e); }}
        }
        playerDataConfig = YamlConfiguration.loadConfiguration(playerDataConfigFile);
    }
    public void savePlayerDataConfig() { if (playerDataConfig == null || playerDataConfigFile == null) return; try { getPlayerDataConfig().save(playerDataConfigFile); } catch (IOException ex) { getLogger().log(Level.SEVERE, "Could not save playerdata.yml", ex); } }
    private void setupPlayerDataConfig() {
        playerDataConfigFile = new File(getDataFolder(), "playerdata.yml");
         if (!playerDataConfigFile.exists()) {
            saveResource("playerdata.yml", false);
            FileConfiguration newCfg = YamlConfiguration.loadConfiguration(playerDataConfigFile);
            if (!newCfg.isConfigurationSection("players")) { newCfg.createSection("players"); try { newCfg.save(playerDataConfigFile); } catch (IOException e) { getLogger().log(Level.SEVERE, "Could not save initial playerdata.yml with players section", e); }}
        }
        playerDataConfig = YamlConfiguration.loadConfiguration(playerDataConfigFile);
    }

    public FileConfiguration getChatDataConfig() { if (chatDataConfig == null) reloadChatDataConfig(); return chatDataConfig; }
    public void reloadChatDataConfig() {
        if (chatDataFile == null) chatDataFile = new File(getDataFolder(), "chatdata.yml");
        if (!chatDataFile.exists()) saveResource("chatdata.yml", false);
        chatDataConfig = YamlConfiguration.loadConfiguration(chatDataFile);
    }
    public void saveChatDataConfig() { if (chatDataConfig == null || chatDataFile == null) return; try { getChatDataConfig().save(chatDataFile); } catch (IOException ex) { getLogger().log(Level.SEVERE, "Could not save chatdata.yml", ex); } }
    private void setupChatDataConfig() {
        chatDataFile = new File(getDataFolder(), "chatdata.yml");
        if (!chatDataFile.exists()) saveResource("chatdata.yml", false);
        chatDataConfig = YamlConfiguration.loadConfiguration(chatDataFile);
        loadMutedPlayers();
    }

    public FileConfiguration getHomesConfig() { if (homesConfig == null) reloadHomesConfig(); return homesConfig; }
    public void reloadHomesConfig() {
        if (homesFile == null) homesFile = new File(getDataFolder(), "homes.yml");
        if (!homesFile.exists()) {
             saveResource("homes.yml", false);
             FileConfiguration newCfg = YamlConfiguration.loadConfiguration(homesFile);
             if(!newCfg.isConfigurationSection("homes")) newCfg.createSection("homes");
             try {newCfg.save(homesFile);} catch (IOException e) {getLogger().log(Level.SEVERE, "Could not save initial homes.yml", e);}
        }
        homesConfig = YamlConfiguration.loadConfiguration(homesFile);
    }
    public void saveHomesConfig() { if (homesConfig == null || homesFile == null) return; try { getHomesConfig().save(homesFile); } catch (IOException ex) { getLogger().log(Level.SEVERE, "Could not save homes.yml", ex); } }
    private void setupHomesConfig() {
        homesFile = new File(getDataFolder(), "homes.yml");
        if (!homesFile.exists()) {
             try {
                homesFile.getParentFile().mkdirs();
                homesFile.createNewFile();
                FileConfiguration cfg = YamlConfiguration.loadConfiguration(homesFile);
                if(!cfg.isConfigurationSection("homes")) cfg.createSection("homes");
                cfg.save(homesFile);
            } catch (IOException e) { getLogger().log(Level.SEVERE, "Could not create homes.yml", e); }
        }
        homesConfig = YamlConfiguration.loadConfiguration(homesFile);
    }

    public FileConfiguration getShopsConfig() { if (shopsConfig == null) reloadShopsConfig(); return shopsConfig; } // Added
    public void reloadShopsConfig() { // Added
        if (shopsFile == null) shopsFile = new File(getDataFolder(), "shops.yml");
        if (!shopsFile.exists()) {
            saveResource("shops.yml", false);
            FileConfiguration newShopsCfg = YamlConfiguration.loadConfiguration(shopsFile);
            if(!newShopsCfg.isConfigurationSection("shops")) newShopsCfg.createSection("shops");
            try {newShopsCfg.save(shopsFile);} catch (IOException e) {getLogger().log(Level.SEVERE, "Could not save initial shops.yml", e);}
        }
        shopsConfig = YamlConfiguration.loadConfiguration(shopsFile);
        InputStream defaultStream = getResource("shops.yml");
        if (defaultStream != null) shopsConfig.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream)));
    }
    public void saveShopsConfig() { // Added
        if (shopsConfig == null || shopsFile == null) return;
        try { getShopsConfig().save(shopsFile); } catch (IOException ex) { getLogger().log(Level.SEVERE, "Could not save shops.yml", ex); }
    }
    private void setupShopsConfig() { // Added
        shopsFile = new File(getDataFolder(), "shops.yml");
        if (!shopsFile.exists()) {
            saveResource("shops.yml", false);
             FileConfiguration newShopsCfg = YamlConfiguration.loadConfiguration(shopsFile);
             if(!newShopsCfg.isConfigurationSection("shops")) {
                newShopsCfg.createSection("shops");
                try {newShopsCfg.save(shopsFile);} catch (IOException e) {getLogger().log(Level.SEVERE, "Could not save initial shops.yml with root key", e);}
             }
        }
        shopsConfig = YamlConfiguration.loadConfiguration(shopsFile);
    }

    // Claims Config Management
    public FileConfiguration getClaimsConfig() { // Added
        if (claimsConfig == null) reloadClaimsConfig();
        return claimsConfig;
    }
    public void reloadClaimsConfig() { // Added
        if (claimsFile == null) claimsFile = new File(getDataFolder(), "claims.yml");
        if (!claimsFile.exists()) {
            saveResource("claims.yml", false);
            FileConfiguration newClaimsCfg = YamlConfiguration.loadConfiguration(claimsFile);
            if(!newClaimsCfg.isConfigurationSection("claims")) newClaimsCfg.createSection("claims");
            try {newClaimsCfg.save(claimsFile);} catch (IOException e) {getLogger().log(Level.SEVERE, "Could not save initial claims.yml", e);}
        }
        claimsConfig = YamlConfiguration.loadConfiguration(claimsFile);
        InputStream defaultStream = getResource("claims.yml");
        if (defaultStream != null) claimsConfig.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream)));
    }
    public void saveClaimsConfig() { // Added
        if (claimsConfig == null || claimsFile == null) return;
        try { getClaimsConfig().save(claimsFile); } catch (IOException ex) { getLogger().log(Level.SEVERE, "Could not save claims.yml", ex); }
    }
    private void setupClaimsConfig() { // Added
        claimsFile = new File(getDataFolder(), "claims.yml");
        if (!claimsFile.exists()) {
            saveResource("claims.yml", false);
             FileConfiguration newClaimsCfg = YamlConfiguration.loadConfiguration(claimsFile);
             if(!newClaimsCfg.isConfigurationSection("claims")) {
                newClaimsCfg.createSection("claims");
                try {newClaimsCfg.save(claimsFile);} catch (IOException e) {getLogger().log(Level.SEVERE, "Could not save initial claims.yml with root key", e);}
             }
        }
        claimsConfig = YamlConfiguration.loadConfiguration(claimsFile);
    }

    // Manager Getters
    public RankManager getRankManager() { return rankManager; }
    public VaultManager getVaultManager() { return vaultManager; }
    public ChatConfig getChatConfig() { return chatConfig; }
    public TeleportConfig getTeleportConfig() { return teleportConfig; }
    public EconomyConfig getEconomyConfig() { return economyConfig; }
    public TeleportManager getTeleportManager() { return teleportManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public ShopManager getShopManager() { return shopManager; }
    public ClaimConfig getClaimConfig() { return claimConfig; }
    public ClaimManager getClaimManager() { return claimManager; } // Added
    public com.example.diamondplugin.command.HomeCommands getHomeCommandsExecutor() { return homeCommandsExecutor; }

    // Config File Getters/Savers for specific managers if needed
    public String getPlayerChannel(UUID playerUuid) { return playerChannels.getOrDefault(playerUuid, chatConfig.getDefaultChannel()); }
    public void setPlayerChannel(UUID playerUuid, String channel) {
        if (channel == null || (!channel.equalsIgnoreCase("global") && !channel.equalsIgnoreCase("local") && !channel.equalsIgnoreCase("staff"))) {
            playerChannels.put(playerUuid, chatConfig.getDefaultChannel()); return;
        }
        playerChannels.put(playerUuid, channel.toLowerCase());
    }
    public void removePlayerChannel(UUID playerUuid) { playerChannels.remove(playerUuid); }

    public boolean isPlayerMuted(UUID playerUuid) {
        MuteData muteData = mutedPlayers.get(playerUuid);
        if (muteData != null) {
            if (muteData.hasExpired()) { unmutePlayer(playerUuid, "System (Expired)"); return false; }
            return true;
        }
        return false;
    }
    public MuteData getMuteData(UUID playerUuid) { return mutedPlayers.get(playerUuid); }
    public void mutePlayer(UUID playerUuid, String reason, long durationMillis, String muterName) {
        long expiresAt = (durationMillis == -1) ? -1 : System.currentTimeMillis() + durationMillis;
        mutedPlayers.put(playerUuid, new MuteData(expiresAt, reason, muterName));
        saveChatDataConfig();
    }
    public boolean unmutePlayer(UUID playerUuid, String unmuterName) {
        if (mutedPlayers.containsKey(playerUuid)) {
            mutedPlayers.remove(playerUuid);
            saveChatDataConfig();
            getLogger().info("Player " + playerUuid + " unmuted by " + unmuterName + ".");
            return true;
        }
        return false;
    }
    private void loadMutedPlayers() {
        mutedPlayers.clear();
        if (getChatDataConfig().contains("muted-players")) {
            ConfigurationSection section = getChatDataConfig().getConfigurationSection("muted-players");
            for (String uuidStr : section.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    long expiresAt = section.getLong(uuidStr + ".expiresAt", -1);
                    String reason = section.getString(uuidStr + ".reason", "Muted by an operator.");
                    String muter = section.getString(uuidStr + ".muter", "Unknown");
                    MuteData muteData = new MuteData(expiresAt, reason, muter);
                    if (!muteData.hasExpired()) mutedPlayers.put(uuid, muteData);
                } catch (IllegalArgumentException e) { getLogger().warning("Invalid UUID in muted-players: " + uuidStr); }
            }
        }
    }

    public void addTeleportRequest(TeleportRequest request) {
        activeTeleportRequestsToTarget.put(request.targetUuid(), request);
        activeTeleportRequestsFromRequester.put(request.requesterUuid(), request);
    }
    public TeleportRequest getTeleportRequestToTarget(UUID targetUuid) { return activeTeleportRequestsToTarget.get(targetUuid); }
    public TeleportRequest getTeleportRequestFromRequester(UUID requesterUuid) { return activeTeleportRequestsFromRequester.get(requesterUuid); }
    public void removeTeleportRequest(TeleportRequest request) {
        if (request == null) return;
        activeTeleportRequestsToTarget.remove(request.targetUuid());
        activeTeleportRequestsFromRequester.remove(request.requesterUuid());
    }
    public void removeTeleportRequestByTarget(UUID targetUuid) {
        TeleportRequest request = activeTeleportRequestsToTarget.remove(targetUuid);
        if (request != null) activeTeleportRequestsFromRequester.remove(request.requesterUuid());
    }
    public void removeTeleportRequestByRequester(UUID requesterUuid) {
        TeleportRequest request = activeTeleportRequestsFromRequester.remove(requesterUuid);
        if (request != null) activeTeleportRequestsToTarget.remove(request.targetUuid());
    }

    private void startPeriodicTasks() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();

                if (rankManager != null) {
                    getRankManager().getPlayerRankDataMap().forEach((uuid, playerData) -> {
                        if (!playerData.isPermanent() && playerData.getExpiresAt() < currentTime) {
                            getLogger().info("Rank " + playerData.getRankName() + " for player " + uuid + " has expired. Reverting to default.");
                            String expiredRankName = playerData.getRankName();
                            Rank expiredRank = getRankManager().getRank(expiredRankName);
                            getRankManager().setPlayerRank(uuid, getRankManager().getDefaultRankName(), -1, true);

                            if (getVaultManager().isVaultAvailable() && expiredRank != null) {
                                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                                Player onlinePlayer = offlinePlayer.isOnline() ? offlinePlayer.getPlayer() : null;
                                if (onlinePlayer != null) {
                                    getVaultManager().removePlayerFromGroup(onlinePlayer, expiredRank.getRankName());
                                    getVaultManager().addPlayerToGroup(onlinePlayer, getRankManager().getDefaultRankName());
                                    onlinePlayer.sendMessage(ChatColor.YELLOW + "Your rank " + expiredRankName + " has expired. You are now " + getRankManager().getDefaultRankName() + ".");
                                } else {
                                     getVaultManager().removePlayerFromGroup(uuid, expiredRank.getRankName());
                                     getVaultManager().addPlayerToGroup(uuid, getRankManager().getDefaultRankName());
                                }
                            }
                        }
                    });
                }

                new HashMap<>(mutedPlayers).forEach((uuid, muteData) -> {
                    if (muteData.hasExpired()) {
                        unmutePlayer(uuid, "System (Expired)");
                        Player onlinePlayer = Bukkit.getPlayer(uuid);
                        if (onlinePlayer != null && onlinePlayer.isOnline()) {
                            onlinePlayer.sendMessage(ChatColor.GREEN + "Your mute has expired.");
                        }
                        getLogger().info("Mute for player " + uuid + " has expired.");
                    }
                });

                if (teleportConfig != null) {
                    long tpaTimeoutMillis = getTeleportConfig().getTpaRequestTimeoutSeconds() * 1000L;
                    new HashMap<>(activeTeleportRequestsToTarget).forEach((targetUuid, request) -> {
                        if ((System.currentTimeMillis() - request.requestTimestamp()) > tpaTimeoutMillis) {
                            removeTeleportRequest(request);
                            Player requester = Bukkit.getPlayer(request.requesterUuid());
                            Player target = Bukkit.getPlayer(request.targetUuid());
                            if (requester != null) requester.sendMessage(ChatColor.RED + "Your teleport request to " + request.targetName() + " has expired.");
                            if (target != null) target.sendMessage(ChatColor.RED + "The teleport request from " + request.requesterName() + " has expired.");
                            getLogger().info("Teleport request between " + request.requesterName() + " and " + request.targetName() + " expired.");
                        }
                    });
                }
            }
        }.runTaskTimer(this, 20L * 30, 20L * 30);
    }
}
