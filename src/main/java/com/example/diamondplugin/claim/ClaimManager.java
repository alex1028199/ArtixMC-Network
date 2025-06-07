package com.example.diamondplugin.claim;

import com.example.diamondplugin.DiamondPlugin;
import com.example.diamondplugin.config.ClaimConfig;
import org.bukkit.ChatColor; // Added import
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class ClaimManager {

    private final DiamondPlugin plugin;
    private final ClaimConfig claimConfig;
    // Store claims: ClaimID -> Claim object
    private final Map<UUID, Claim> claimsById = new ConcurrentHashMap<>();
    // Spatial index: WorldName -> List of claims in that world (can be optimized with Quadtree later)
    private final Map<String, List<Claim>> claimsByWorld = new ConcurrentHashMap<>();

    public ClaimManager(DiamondPlugin plugin) {
        this.plugin = plugin;
        this.claimConfig = plugin.getClaimConfig();
        loadClaims();
    }

    public void loadClaims() {
        claimsById.clear();
        claimsByWorld.clear();
        ConfigurationSection claimsSection = plugin.getClaimsConfig().getConfigurationSection("claims");
        if (claimsSection == null) {
            plugin.getLogger().info("No 'claims' section found in claims.yml. Starting fresh.");
            return;
        }

        for (String claimIdStr : claimsSection.getKeys(false)) {
            try {
                UUID claimID = UUID.fromString(claimIdStr);
                ConfigurationSection cs = claimsSection.getConfigurationSection(claimIdStr);
                if (cs == null) continue;

                UUID ownerUUID = UUID.fromString(cs.getString("owner"));
                String worldName = cs.getString("world");
                int x1 = cs.getInt("x1");
                int z1 = cs.getInt("z1");
                int x2 = cs.getInt("x2");
                int z2 = cs.getInt("z2");
                long creationDate = cs.getLong("creationDate", System.currentTimeMillis());

                Set<UUID> trusted = new HashSet<>();
                List<String> trustedStrs = cs.getStringList("trusted");
                for(String trustedStr : trustedStrs) {
                    try { trusted.add(UUID.fromString(trustedStr)); }
                    catch (IllegalArgumentException e) { plugin.getLogger().warning("Invalid trusted UUID " + trustedStr + " for claim " + claimID); }
                }

                Map<String, Boolean> flags = new HashMap<>();
                ConfigurationSection flagsSec = cs.getConfigurationSection("flags");
                if (flagsSec != null) {
                    for (String flagKey : flagsSec.getKeys(false)) {
                        flags.put(flagKey.toLowerCase(), flagsSec.getBoolean(flagKey));
                    }
                }

                Claim claim = new Claim(claimID, ownerUUID, worldName, x1, z1, x2, z2, creationDate, trusted, flags);
                claimsById.put(claimID, claim);
                claimsByWorld.computeIfAbsent(worldName, k -> new ArrayList<>()).add(claim);

            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID for claim ID or owner in claims.yml: " + claimIdStr + ". Error: " + e.getMessage());
            } catch (Exception e) {
                 plugin.getLogger().log(Level.SEVERE, "Error loading claim " + claimIdStr + ": " + e.getMessage(), e);
            }
        }
        plugin.getLogger().info("Loaded " + claimsById.size() + " claims.");
    }

    public void saveClaims() {
        ConfigurationSection claimsSection = plugin.getClaimsConfig().createSection("claims"); // Overwrites existing
        for (Claim claim : claimsById.values()) {
            ConfigurationSection cs = claimsSection.createSection(claim.getClaimID().toString());
            cs.set("owner", claim.getOwnerUUID().toString());
            cs.set("world", claim.getWorldName());
            cs.set("x1", claim.getMinX());
            cs.set("z1", claim.getMinZ());
            cs.set("x2", claim.getMaxX());
            cs.set("z2", claim.getMaxZ());
            cs.set("creationDate", claim.getCreationDate());
            cs.set("trusted", claim.getTrustedPlayers().stream().map(UUID::toString).collect(Collectors.toList()));
            ConfigurationSection flagsSec = cs.createSection("flags");
            for(Map.Entry<String, Boolean> flagEntry : claim.getFlags().entrySet()){
                flagsSec.set(flagEntry.getKey(), flagEntry.getValue());
            }
        }
        plugin.saveClaimsConfig();
        plugin.getLogger().info("Saved " + claimsById.size() + " claims.");
    }

    private void saveClaim(Claim claim) { // For saving a single claim efficiently if needed
        ConfigurationSection claimsSection = plugin.getClaimsConfig().getConfigurationSection("claims");
        if (claimsSection == null) {
            claimsSection = plugin.getClaimsConfig().createSection("claims");
        }
        ConfigurationSection cs = claimsSection.createSection(claim.getClaimID().toString());
        cs.set("owner", claim.getOwnerUUID().toString());
        cs.set("world", claim.getWorldName());
        cs.set("x1", claim.getMinX());
        cs.set("z1", claim.getMinZ());
        cs.set("x2", claim.getMaxX());
        cs.set("z2", claim.getMaxZ());
        cs.set("creationDate", claim.getCreationDate());
        cs.set("trusted", claim.getTrustedPlayers().stream().map(UUID::toString).collect(Collectors.toList()));
        ConfigurationSection flagsSec = cs.createSection("flags");
        for(Map.Entry<String, Boolean> flagEntry : claim.getFlags().entrySet()){
            flagsSec.set(flagEntry.getKey(), flagEntry.getValue());
        }
        plugin.saveClaimsConfig();
    }


    public Claim createClaim(Player player, Location corner1, Location corner2) {
        if (!corner1.getWorld().equals(corner2.getWorld())) {
            player.sendMessage(ChatColor.RED + "Claim corners must be in the same world.");
            return null;
        }

        Claim newClaim = new Claim(player.getUniqueId(), corner1.getWorld().getName(),
                                   corner1.getBlockX(), corner1.getBlockZ(),
                                   corner2.getBlockX(), corner2.getBlockZ());

        if (newClaim.getArea() < claimConfig.getMinClaimSize() * claimConfig.getMinClaimSize() &&
            (newClaim.getSizeX() < claimConfig.getMinClaimSize() || newClaim.getSizeZ() < claimConfig.getMinClaimSize())) {
            player.sendMessage(ChatColor.RED + "Claim is too small. Minimum size is " + claimConfig.getMinClaimSize() + "x" + claimConfig.getMinClaimSize() + " blocks in at least one dimension pair.");
            return null;
        }

        if (isOverlapping(newClaim)) {
            player.sendMessage(ChatColor.RED + "This claim overlaps with an existing claim.");
            return null;
        }

        List<Claim> ownerClaims = getClaimsByOwner(player.getUniqueId());
        int maxClaims = plugin.getTeleportManager().getMaxHomes(player); // Reusing home limit logic for now - TODO: Separate claim limits
         if(player.hasPermission("diamondplugin.claim.limit.unlimited")) {
            maxClaims = Integer.MAX_VALUE;
        } else {
            maxClaims = claimConfig.getDefaultMaxClaimsPerPlayer(); // Default from config
            for (int i = 100; i > 0; i--) {
                if (player.hasPermission("diamondplugin.claim.limit." + i)) {
                    maxClaims = i;
                    break;
                }
            }
        }

        if (ownerClaims.size() >= maxClaims) {
            player.sendMessage(ChatColor.RED + "You have reached your maximum claim limit (" + maxClaims + ").");
            return null;
        }

        // TODO: Economy integration - deduct cost if enabled

        claimsById.put(newClaim.getClaimID(), newClaim);
        claimsByWorld.computeIfAbsent(newClaim.getWorldName(), k -> new ArrayList<>()).add(newClaim);
        saveClaim(newClaim); // Save the new claim

        player.sendMessage(ChatColor.GREEN + "Land claimed successfully! ID: " + newClaim.getClaimID().toString().substring(0,8));
        return newClaim;
    }

    public boolean deleteClaim(Claim claim, Player requester) {
        if (claim == null) return false;
        if (!claim.getOwnerUUID().equals(requester.getUniqueId()) && !requester.hasPermission("diamondplugin.admin.unclaim")) {
            requester.sendMessage(ChatColor.RED + "You do not own this claim or lack permission to unclaim it.");
            return false;
        }

        claimsById.remove(claim.getClaimID());
        List<Claim> worldClaims = claimsByWorld.get(claim.getWorldName());
        if (worldClaims != null) {
            worldClaims.remove(claim);
            if (worldClaims.isEmpty()) {
                claimsByWorld.remove(claim.getWorldName());
            }
        }
        // Remove the specific claim section from claims.yml
        ConfigurationSection claimsSection = plugin.getClaimsConfig().getConfigurationSection("claims");
        if(claimsSection != null) {
            claimsSection.set(claim.getClaimID().toString(), null);
        }
        plugin.saveClaimsConfig();
        requester.sendMessage(ChatColor.GREEN + "Claim (ID: ..." + claim.getClaimID().toString().substring(0,8) + ") has been unclaimed.");
        return true;
    }

    public Claim getClaimAt(Location location) {
        List<Claim> worldClaims = claimsByWorld.get(location.getWorld().getName());
        if (worldClaims == null) return null;

        for (Claim claim : worldClaims) {
            if (claim.contains(location)) {
                return claim;
            }
        }
        return null;
    }

    public List<Claim> getClaimsByOwner(UUID ownerUuid) {
        return claimsById.values().stream()
                .filter(claim -> claim.getOwnerUUID().equals(ownerUuid))
                .collect(Collectors.toList());
    }

    public boolean isOverlapping(Claim newClaim) {
        List<Claim> worldClaims = claimsByWorld.get(newClaim.getWorldName());
        if (worldClaims == null) return false;

        for (Claim existingClaim : worldClaims) {
            if (existingClaim.intersects(newClaim)) {
                return true;
            }
        }
        return false;
    }
}
