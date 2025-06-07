package com.example.diamondplugin.command;

import com.example.diamondplugin.DiamondPlugin;
import com.example.diamondplugin.claim.Claim;
import com.example.diamondplugin.claim.ClaimManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ClaimCommands implements CommandExecutor, TabCompleter {

    private final DiamondPlugin plugin;
    private final ClaimManager claimManager;

    public ClaimCommands(DiamondPlugin plugin) {
        this.plugin = plugin;
        this.claimManager = plugin.getClaimManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        Player player = (Player) sender;

        switch (command.getName().toLowerCase()) {
            case "claim": // Currently, primary claiming is via the tool. This could be an alternative.
                player.sendMessage(ChatColor.YELLOW + "Please use the claim tool (" + plugin.getClaimConfig().getClaimTool().toString().replace("_", " ").toLowerCase() + ") to define your claim area by right-clicking two corners.");
                // Potentially, if player has selections: claimManager.createClaim(player, sel1, sel2);
                return true;
            case "unclaim":
            case "abandonclaim":
                return handleUnclaim(player, args);
            case "claimslist":
            case "myclaims":
                return handleListClaims(player);
        }
        return false;
    }

    private boolean handleUnclaim(Player player, String[] args) {
        if (!player.hasPermission("diamondplugin.command.claim")) { // Basic claim permission to unclaim own
            player.sendMessage(ChatColor.RED + "You don't have permission to unclaim land.");
            return true;
        }

        Claim claimAtLocation = claimManager.getClaimAt(player.getLocation());

        if (args.length > 0 && player.hasPermission("diamondplugin.admin.unclaim")) {
            // Admin trying to unclaim a specific claim by ID (not fully implemented here, needs ID lookup)
            // For now, admin unclaim also works by standing in the claim.
             if (claimAtLocation == null) {
                player.sendMessage(ChatColor.RED + "You are not standing in any claim to unclaim (admin).");
                return true;
            }
            // Admin can unclaim any claim they are standing in.
        } else {
             if (claimAtLocation == null) {
                player.sendMessage(ChatColor.RED + "You are not standing in any of your claims.");
                return true;
            }
            if (!claimAtLocation.getOwnerUUID().equals(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "You do not own this claim. Use /abandonclaim while standing inside your claim.");
                return true;
            }
        }

        // Confirmation might be good here, but for now, direct delete.
        if (claimManager.deleteClaim(claimAtLocation, player)) {
            // Message is sent by deleteClaim
        } else {
            // This case should ideally be caught by earlier checks.
            player.sendMessage(ChatColor.RED + "Could not unclaim the land here.");
        }
        return true;
    }

    private boolean handleListClaims(Player player) {
        if (!player.hasPermission("diamondplugin.command.claim")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to list claims.");
            return true;
        }
        List<Claim> playerClaims = claimManager.getClaimsByOwner(player.getUniqueId());
        if (playerClaims.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "You have no claims. Use the claim tool to make one!");
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "--- Your Claims ---");
        for (Claim claim : playerClaims) {
            TextComponent claimComponent = new TextComponent("- ID: ..." + claim.getClaimID().toString().substring(0, 8) +
                    " (World: " + claim.getWorldName() +
                    ", Size: " + claim.getSizeX() + "x" + claim.getSizeZ() + ")");
            claimComponent.setColor(net.md_5.bungee.api.ChatColor.GRAY);
            // Make it clickable to teleport to the claim's approximate center (for later)
            // Location center = new Location(Bukkit.getWorld(claim.getWorldName()),
            //                                (claim.getMinX() + claim.getMaxX()) / 2.0,
            //                                player.getLocation().getY(), // Or a fixed Y like 64
            //                                (claim.getMinZ() + claim.getMaxZ()) / 2.0);
            // claimComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp " + center.getX() + " " + center.getY() + " " + center.getZ()));
            player.spigot().sendMessage(claimComponent);
        }
        return true;
    }


    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        // No specific tab completions for these basic claim commands yet.
        // /unclaim [id] for admins could list claim IDs they are near or all if too many.
        return Collections.emptyList();
    }
}
