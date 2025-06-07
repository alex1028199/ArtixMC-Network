package com.example.diamondplugin.rank;

import com.example.diamondplugin.DiamondPlugin;
import com.example.diamondplugin.permission.VaultManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class RankManager {

    private final DiamondPlugin plugin;
    private final VaultManager vaultManager;
    private final Map<String, Rank> ranks = new HashMap<>();
    // Stores player's current rank name (lowercase) and expiry time (-1 for permanent)
    private final Map<UUID, PlayerRankData> playerRankDataMap = new HashMap<>();
    private String defaultRankName = "member"; // Default rank name, lowercase

    public static class PlayerRankData {
        String rankName; // lowercase
        long expiresAt; // timestamp in millis, -1 for permanent

        public PlayerRankData(String rankName, long expiresAt) {
            this.rankName = rankName.toLowerCase();
            this.expiresAt = expiresAt;
        }

        public String getRankName() {
            return rankName;
        }

        public long getExpiresAt() {
            return expiresAt;
        }

        public boolean isPermanent() {
            return expiresAt == -1;
        }

        public boolean hasExpired() {
            if (isPermanent()) {
                return false;
            }
            return System.currentTimeMillis() > expiresAt;
        }
    }


    public RankManager(DiamondPlugin plugin) {
        this.plugin = plugin;
        this.vaultManager = plugin.getVaultManager();
        loadRanks();
        loadPlayerData();
    }

    private void loadRanks() {
        ranks.clear();
        ConfigurationSection ranksSection = plugin.getRankConfig().getConfigurationSection("ranks");
        if (ranksSection == null) {
            plugin.getLogger().info("No ranks section found in ranks.yml. Attempting to create default ranks.");
            // Ensure default ranks are created if the section is missing
            // These calls to createRank will also saveRanks()
            createRankInternal("Member", "&7[Member] ", new ArrayList<>(), true);
            createRankInternal("Moderator", "&a[Moderator] ", new ArrayList<>(), true);
            createRankInternal("Admin", "&c[Admin] ", new ArrayList<>(), true);
            // If createRankInternal doesn't save immediately or if we want to ensure defaults are set:
            if(ranks.isEmpty()) { // Still empty after attempt means createRankInternal might not have saved or run.
                 plugin.getLogger().severe("Failed to create default ranks from loadRanks. ranks.yml might be problematic.");
            }
            // DefaultRankName is already "member"
            return;
        }

        for (String rankKey : ranksSection.getKeys(false)) {
            String lowerRankKey = rankKey.toLowerCase(); // Use lowercase for map key consistency
            String prefix = ranksSection.getString(rankKey + ".prefix", "&7[" + rankKey + "] ");
            List<String> permissions = ranksSection.getStringList(rankKey + ".permissions");
            // Store with original rankKey for display/config saving, but use lowerRankKey for map key
            ranks.put(lowerRankKey, new Rank(rankKey, prefix, permissions));
            plugin.getLogger().info("Loaded rank: " + rankKey);
        }

        // Ensure defaultRankName is valid after loading all ranks
        if (ranks.isEmpty()) {
             plugin.getLogger().severe("No ranks were loaded from ranks.yml and the ranks map is empty. This is critical.");
             // Attempt to create a failsafe default if none exist at all
             createRankInternal("Member", "&7[Member] ", new ArrayList<>(), true); // This will save
             defaultRankName = "member"; // Ensure defaultRankName is set
        } else if (!ranks.containsKey(defaultRankName.toLowerCase())) {
            // If the configured defaultRankName (e.g. "member") doesn't exist, pick the first available one.
            defaultRankName = ranks.keySet().stream().findFirst().get(); // get() is safe due to ranks.isEmpty() check above
            plugin.getLogger().warning("Default rank 'member' not found. Setting default rank to the first loaded rank: " + defaultRankName);
        }
    }

    // Internal method to create ranks, optionally skipping save for bulk loads
    private boolean createRankInternal(String rankName, String prefix, List<String> permissions, boolean save) {
        if (ranks.containsKey(rankName.toLowerCase())) {
            return false;
        }
        Rank newRank = new Rank(rankName, prefix, permissions);
        ranks.put(rankName.toLowerCase(), newRank);
        if (save) {
            saveRanks();
        }
        return true;
    }


    public void saveRanks() {
        plugin.getRankConfig().set("ranks", null); // Clear existing ranks section
        for (Rank rank : ranks.values()) {
            // Use the original casing for keys in YAML for readability
            String configKey = "ranks." + rank.getRankName();
            plugin.getRankConfig().set(configKey + ".prefix", rank.getPrefix());
            plugin.getRankConfig().set(configKey + ".permissions", rank.getPermissions());
        }
        plugin.saveRankConfig();
    }

    private void loadPlayerData() {
        playerRankDataMap.clear();
        ConfigurationSection playerDataSection = plugin.getPlayerDataConfig().getConfigurationSection("players");
        if (playerDataSection != null) {
            for (String uuidString : playerDataSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    String rankName = playerDataSection.getString(uuidString + ".rank");
                    long expiresAt = playerDataSection.getLong(uuidString + ".expiresAt", -1);

                    if (rankName != null && ranks.containsKey(rankName.toLowerCase())) {
                        playerRankDataMap.put(uuid, new PlayerRankData(rankName, expiresAt));
                    } else {
                        playerRankDataMap.put(uuid, new PlayerRankData(defaultRankName, -1)); // Assign default
                        plugin.getLogger().warning("Player " + uuidString + " had an invalid rank '" + rankName + "'. Assigning default rank.");
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID string in playerdata.yml: " + uuidString);
                }
            }
        }
    }

    public void savePlayerData() {
        plugin.getPlayerDataConfig().set("players", null); // Clear existing player data
        for (Map.Entry<UUID, PlayerRankData> entry : playerRankDataMap.entrySet()) {
            String uuidString = entry.getKey().toString();
            PlayerRankData data = entry.getValue();
            plugin.getPlayerDataConfig().set("players." + uuidString + ".rank", data.getRankName());
            plugin.getPlayerDataConfig().set("players." + uuidString + ".expiresAt", data.getExpiresAt());
        }
        plugin.savePlayerDataConfig();
    }

    public boolean createRank(String rankName, String prefix) {
        return createRankInternal(rankName, prefix, new ArrayList<>(), true);
    }

    public boolean deleteRank(String rankName) {
        String lowerRankName = rankName.toLowerCase();
        if (!ranks.containsKey(lowerRankName)) {
            return false;
        }
        if (lowerRankName.equals(defaultRankName.toLowerCase()) && ranks.size() == 1) {
            plugin.getLogger().warning("Cannot delete the last remaining rank which is also the default rank.");
            return false;
        }

        Rank removedRank = ranks.remove(lowerRankName);

        // Update players who had this rank to the default rank
        // Also remove from Vault if applicable
        if (removedRank != null && vaultManager.isVaultAvailable()) {
            Bukkit.getOnlinePlayers().stream()
                .filter(p -> getPlayerRankName(p.getUniqueId()).equalsIgnoreCase(lowerRankName))
                .forEach(p -> vaultManager.removePlayerFromGroup(p, removedRank.getRankName()));
            // For offline players, this is trickier without iterating all player data files or a database
        }

        List<UUID> toUpdateToDefault = new ArrayList<>();
        for (Map.Entry<UUID, PlayerRankData> entry : playerRankDataMap.entrySet()) {
            if (entry.getValue().getRankName().equalsIgnoreCase(lowerRankName)) {
                toUpdateToDefault.add(entry.getKey());
            }
        }
        for (UUID uuid : toUpdateToDefault) {
            setPlayerRank(uuid, defaultRankName, -1, false); // Set to default, permanent, no immediate save
        }


        if (lowerRankName.equals(defaultRankName.toLowerCase())) {
            defaultRankName = ranks.keySet().stream().findFirst().orElse("member");
            plugin.getLogger().info("Default rank was deleted. New default rank is: " + defaultRankName);
        }

        saveRanks(); // Save rank definition changes
        savePlayerData(); // Save player data changes
        return true;
    }

    public Rank getRank(String rankName) {
        if (rankName == null) return null;
        return ranks.get(rankName.toLowerCase());
    }

    public boolean setRankPrefix(String rankName, String prefix) {
        Rank rank = getRank(rankName);
        if (rank == null) {
            return false;
        }
        rank.setPrefix(prefix);
        saveRanks();
        return true;
    }

    public Collection<Rank> getAllRanks() {
        return Collections.unmodifiableCollection(ranks.values());
    }

    // Overload for permanent ranks
    public boolean setPlayerRank(UUID playerUuid, String rankName) {
        return setPlayerRank(playerUuid, rankName, -1, true);
    }

    // Main method for setting player rank with duration
    public boolean setPlayerRank(UUID playerUuid, String rankName, long durationMillis, boolean save) {
        String lowerRankName = rankName.toLowerCase();
        Rank newRank = getRank(lowerRankName);
        if (newRank == null) {
            plugin.getLogger().warning("Attempted to set player " + playerUuid + " to non-existent rank: " + rankName);
            return false;
        }

        PlayerRankData oldPlayerData = playerRankDataMap.get(playerUuid);
        String oldRankName = (oldPlayerData != null) ? oldPlayerData.getRankName() : defaultRankName.toLowerCase();
        Rank oldRank = getRank(oldRankName);

        long expiryTimestamp = (durationMillis > 0) ? System.currentTimeMillis() + durationMillis : -1;
        playerRankDataMap.put(playerUuid, new PlayerRankData(lowerRankName, expiryTimestamp));

        if (vaultManager.isVaultAvailable()) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUuid);
            Player player = offlinePlayer.isOnline() ? offlinePlayer.getPlayer() : null;

            if (oldRank != null && !oldRank.getRankName().equalsIgnoreCase(newRank.getRankName())) {
                 if (player != null) vaultManager.removePlayerFromGroup(player, oldRank.getRankName());
                 else vaultManager.removePlayerFromGroup(playerUuid, oldRank.getRankName());
            }
            if (player != null) vaultManager.addPlayerToGroup(player, newRank.getRankName());
            else vaultManager.addPlayerToGroup(playerUuid, newRank.getRankName());
        }

        if (save) {
            savePlayerData();
        }
        return true;
    }

    public PlayerRankData getPlayerRankData(UUID playerUuid) {
        PlayerRankData data = playerRankDataMap.get(playerUuid);
        if (data == null) {
            return new PlayerRankData(defaultRankName, -1); // Return default if no data
        }
        if (data.hasExpired()) {
            plugin.getLogger().info("Player " + playerUuid + "'s rank " + data.getRankName() + " has expired. Reverting to default.");
            setPlayerRank(playerUuid, defaultRankName, -1, true); // Revert to default, permanent
            return playerRankDataMap.getOrDefault(playerUuid, new PlayerRankData(defaultRankName, -1)); // Get the updated (default) data
        }
        return data;
    }


    public Rank getPlayerRank(UUID playerUuid) {
        PlayerRankData data = getPlayerRankData(playerUuid);
        return ranks.get(data.getRankName());
    }

    public String getPlayerRankName(UUID playerUuid) {
        return getPlayerRankData(playerUuid).getRankName();
    }

    public boolean removePlayerRank(UUID playerUuid) { // Effectively sets to default
        PlayerRankData currentData = playerRankDataMap.get(playerUuid);
        String currentRankName = (currentData != null) ? currentData.getRankName() : null;
        Rank currentRank = (currentRankName != null) ? getRank(currentRankName) : null;

        boolean changed = setPlayerRank(playerUuid, defaultRankName, -1, true); // Set to default, permanent

        if (vaultManager.isVaultAvailable() && currentRank != null && !currentRank.getRankName().equalsIgnoreCase(defaultRankName)) {
             OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUuid);
             Player player = offlinePlayer.isOnline() ? offlinePlayer.getPlayer() : null;
             if(player != null) {
                 vaultManager.removePlayerFromGroup(player, currentRank.getRankName());
                 vaultManager.addPlayerToGroup(player, defaultRankName);
             } else {
                 vaultManager.removePlayerFromGroup(playerUuid, currentRank.getRankName());
                 vaultManager.addPlayerToGroup(playerUuid, defaultRankName);
             }
        }
        return changed;
    }

    public String getDefaultRankName() {
        return defaultRankName;
    }

    public Rank getDefaultRank() {
        return ranks.get(defaultRankName.toLowerCase());
    }

    public Map<UUID, PlayerRankData> getPlayerRankDataMap() {
        return Collections.unmodifiableMap(playerRankDataMap);
    }
}
