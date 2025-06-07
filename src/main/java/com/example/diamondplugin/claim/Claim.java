package com.example.diamondplugin.claim;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.HashMap;

public class Claim {
    private final UUID claimID;
    private final UUID ownerUUID;
    private final String worldName;
    private int corner1X, corner1Z;
    private int corner2X, corner2Z;
    private final long creationDate;
    private final Set<UUID> trustedPlayers;
    private final Map<String, Boolean> flags;

    public Claim(UUID ownerUUID, String worldName, int x1, int z1, int x2, int z2) {
        this.claimID = UUID.randomUUID();
        this.ownerUUID = ownerUUID;
        this.worldName = worldName;
        normalizeCorners(x1, z1, x2, z2);
        this.creationDate = System.currentTimeMillis();
        this.trustedPlayers = new HashSet<>();
        this.flags = new HashMap<>();
    }

    // Constructor for loading from storage
    public Claim(UUID claimID, UUID ownerUUID, String worldName, int x1, int z1, int x2, int z2, long creationDate, Set<UUID> trustedPlayers, Map<String, Boolean> flags) {
        this.claimID = claimID;
        this.ownerUUID = ownerUUID;
        this.worldName = worldName;
        normalizeCorners(x1, z1, x2, z2);
        this.creationDate = creationDate;
        this.trustedPlayers = trustedPlayers != null ? trustedPlayers : new HashSet<>();
        this.flags = flags != null ? flags : new HashMap<>();
    }

    private void normalizeCorners(int x1, int z1, int x2, int z2) {
        this.corner1X = Math.min(x1, x2);
        this.corner1Z = Math.min(z1, z2);
        this.corner2X = Math.max(x1, x2);
        this.corner2Z = Math.max(z1, z2);
    }

    public UUID getClaimID() {
        return claimID;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getCorner1X() {
        return corner1X;
    }

    public int getCorner1Z() {
        return corner1Z;
    }

    public int getCorner2X() {
        return corner2X;
    }

    public int getCorner2Z() {
        return corner2Z;
    }

    public long getCreationDate() {
        return creationDate;
    }

    public Set<UUID> getTrustedPlayers() {
        return trustedPlayers; // Consider returning a copy: new HashSet<>(trustedPlayers)
    }

    public Map<String, Boolean> getFlags() {
        return flags; // Consider returning a copy: new HashMap<>(flags)
    }

    public void setCorners(int x1, int z1, int x2, int z2) { // If resizing is allowed
        normalizeCorners(x1, z1, x2, z2);
    }

    public boolean contains(Location location) {
        if (location == null || !location.getWorld().getName().equals(this.worldName)) {
            return false;
        }
        return contains(location.getBlockX(), location.getBlockZ(), location.getWorld().getName());
    }

    public boolean contains(int x, int z, String worldName) {
        if (!this.worldName.equals(worldName)) {
            return false;
        }
        return x >= corner1X && x <= corner2X && z >= corner1Z && z <= corner2Z;
    }

    public int getMinX() { return corner1X; }
    public int getMaxX() { return corner2X; }
    public int getMinZ() { return corner1Z; }
    public int getMaxZ() { return corner2Z; }

    public int getSizeX() {
        return (corner2X - corner1X) + 1;
    }
    public int getSizeZ() {
        return (corner2Z - corner1Z) + 1;
    }
    public int getArea() {
        return getSizeX() * getSizeZ();
    }

    public boolean intersects(Claim other) {
        if (!this.worldName.equals(other.worldName)) {
            return false;
        }
        // Check if one rectangle is on left side of other
        if (this.getMaxX() < other.getMinX() || other.getMaxX() < this.getMinX()) {
            return false;
        }
        // Check if one rectangle is above other
        if (this.getMaxZ() < other.getMinZ() || other.getMaxZ() < this.getMinZ()) {
            return false;
        }
        return true; // Overlapping
    }

    public void trustPlayer(UUID playerUuid) {
        trustedPlayers.add(playerUuid);
    }

    public void untrustPlayer(UUID playerUuid) {
        trustedPlayers.remove(playerUuid);
    }

    public boolean isTrusted(UUID playerUuid) {
        return ownerUUID.equals(playerUuid) || trustedPlayers.contains(playerUuid);
    }

    public void setFlag(String flagName, boolean value) {
        flags.put(flagName.toLowerCase(), value);
    }

    public boolean getFlag(String flagName) {
        return flags.getOrDefault(flagName.toLowerCase(), false); // Default to false if flag not set
    }

    // Consider adding equals and hashCode methods if storing Claims in Sets/Maps directly
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Claim claim = (Claim) o;
        return claimID.equals(claim.claimID);
    }

    @Override
    public int hashCode() {
        return claimID.hashCode();
    }
}
