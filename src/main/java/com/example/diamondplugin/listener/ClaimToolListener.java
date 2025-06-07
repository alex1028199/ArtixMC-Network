package com.example.diamondplugin.listener;

import com.example.diamondplugin.DiamondPlugin;
import com.example.diamondplugin.claim.ClaimManager;
import com.example.diamondplugin.config.ClaimConfig;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack; // Added import

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClaimToolListener implements Listener {

    private final DiamondPlugin plugin;
    private final ClaimManager claimManager;
    private final ClaimConfig claimConfig;
    private final Map<UUID, Location> firstCornerSelections = new HashMap<>();

    public ClaimToolListener(DiamondPlugin plugin) {
        this.plugin = plugin;
        this.claimManager = plugin.getClaimManager();
        this.claimConfig = plugin.getClaimConfig();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand.getType() != claimConfig.getClaimTool()) {
            return;
        }

        // Ensure the interaction is with a block and it's a right-click for setting pos
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            if (event.getAction() == Action.LEFT_CLICK_BLOCK && firstCornerSelections.containsKey(player.getUniqueId())) {
                // Left-click with tool while having a selection cancels it
                firstCornerSelections.remove(player.getUniqueId());
                player.sendMessage(ChatColor.YELLOW + "Claim corner selection cancelled.");
                event.setCancelled(true);
            }
            return;
        }

        // Make sure it's the main hand to avoid double calls
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        event.setCancelled(true); // Prevent default golden shovel behavior (e.g. tilling)

        Location clickedBlockLocation = event.getClickedBlock().getLocation();

        if (!firstCornerSelections.containsKey(player.getUniqueId())) {
            firstCornerSelections.put(player.getUniqueId(), clickedBlockLocation);
            player.sendMessage(ChatColor.GREEN + "First claim corner set at X: " + clickedBlockLocation.getBlockX() + ", Z: " + clickedBlockLocation.getBlockZ() + ". Right-click the second corner.");
        } else {
            Location corner1 = firstCornerSelections.remove(player.getUniqueId());
            Location corner2 = clickedBlockLocation;

            if (!corner1.getWorld().equals(corner2.getWorld())) {
                player.sendMessage(ChatColor.RED + "Both corners must be in the same world!");
                firstCornerSelections.put(player.getUniqueId(), corner2); // Keep the second selection as the new first
                player.sendMessage(ChatColor.YELLOW + "Second corner selection is now your first corner. Please select the second corner again in this world ("+corner2.getWorld().getName()+").");
                return;
            }

            // Attempt to create the claim
            // The ClaimManager.createClaim handles feedback to the player for success/failure
            claimManager.createClaim(player, corner1, corner2);
            // No need to clear firstCornerSelections here, it's already removed.
        }
    }
}
