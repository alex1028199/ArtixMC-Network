package com.example.diamondplugin.command;

import com.example.diamondplugin.DiamondPlugin;
import com.example.diamondplugin.config.TeleportConfig;
import com.example.diamondplugin.teleport.TeleportRequest;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class TpaCommands implements CommandExecutor, TabCompleter {

    private final DiamondPlugin plugin;
    private final TeleportConfig teleportConfig;
    private final HomeCommands homeCommands; // For delay/cooldown system access

    public TpaCommands(DiamondPlugin plugin, HomeCommands homeCommands) {
        this.plugin = plugin;
        this.teleportConfig = plugin.getTeleportConfig();
        this.homeCommands = homeCommands;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("These commands can only be used by players.");
            return true;
        }
        Player player = (Player) sender;

        switch (command.getName().toLowerCase()) {
            case "tpa":
                return handleTpa(player, args, false); // toRequester = false
            case "tpahere":
                return handleTpa(player, args, true);  // toRequester = true
            case "tpaccept":
                return handleTpAccept(player, args);
            case "tpdeny":
                return handleTpDeny(player, args);
            case "tpcancel":
                return handleTpCancel(player);
        }
        return false;
    }

    private boolean handleTpa(Player requester, String[] args, boolean toRequester) {
        String requiredPerm = toRequester ? "diamondplugin.command.tpahere" : "diamondplugin.command.tpa";
        if (!requester.hasPermission(requiredPerm)) {
            requester.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        if (args.length < 1) {
            requester.sendMessage(ChatColor.RED + "Usage: /" + (toRequester ? "tpahere" : "tpa") + " <player_name>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            requester.sendMessage(ChatColor.RED + "Player '" + args[0] + "' not found or is not online.");
            return true;
        }
        if (target.equals(requester)) {
            requester.sendMessage(ChatColor.RED + "You cannot send a teleport request to yourself.");
            return true;
        }

        // Check if there's an existing request from this requester or to this target that's still active
        if (plugin.getTeleportRequestFromRequester(requester.getUniqueId()) != null) {
            requester.sendMessage(ChatColor.RED + "You already have an outgoing teleport request. Use /tpcancel first.");
            return true;
        }
         if (plugin.getTeleportRequestToTarget(target.getUniqueId()) != null &&
            plugin.getTeleportRequestToTarget(target.getUniqueId()).requesterUuid().equals(requester.getUniqueId())) {
            requester.sendMessage(ChatColor.RED + "You have already sent a request to this player.");
            return true;
        }


        TeleportRequest request = new TeleportRequest(
                requester.getUniqueId(), requester.getName(),
                target.getUniqueId(), target.getName(),
                System.currentTimeMillis(),
                toRequester
        );
        plugin.addTeleportRequest(request);

        String actionVerb = toRequester ? "teleport to you" : "teleport to them";
        String targetActionVerb = toRequester ? "teleport to " + requester.getName() : requester.getName() + " to teleport to them";

        requester.sendMessage(ChatColor.GREEN + "Teleport request sent to " + target.getName() + " for them to " + actionVerb + ".");

        TextComponent acceptButton = new TextComponent("[Accept]");
        acceptButton.setColor(net.md_5.bungee.api.ChatColor.GREEN);
        acceptButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept " + requester.getName()));
        acceptButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Click to accept").color(net.md_5.bungee.api.ChatColor.GREEN).create()));

        TextComponent denyButton = new TextComponent("[Deny]");
        denyButton.setColor(net.md_5.bungee.api.ChatColor.RED);
        denyButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpdeny " + requester.getName()));
        denyButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Click to deny").color(net.md_5.bungee.api.ChatColor.RED).create()));

        TextComponent messageToTarget = new TextComponent(requester.getName() + " has requested to " + targetActionVerb + ". This request will expire in " + teleportConfig.getTpaRequestTimeoutSeconds() + "s. ");
        messageToTarget.setColor(net.md_5.bungee.api.ChatColor.YELLOW);
        messageToTarget.addExtra(acceptButton);
        messageToTarget.addExtra(" ");
        messageToTarget.addExtra(denyButton);
        target.spigot().sendMessage(messageToTarget);

        return true;
    }

    private boolean handleTpAccept(Player player, String[] args) { // Player is the target who is accepting
        if (!player.hasPermission("diamondplugin.command.tpaccept")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        TeleportRequest request;
        if (args.length > 0) {
            Player requester = Bukkit.getPlayer(args[0]);
            if (requester == null) {
                player.sendMessage(ChatColor.RED + "Player '" + args[0] + "' not found.");
                return true;
            }
            request = plugin.getTeleportRequestToTarget(player.getUniqueId());
            if(request == null || !request.requesterUuid().equals(requester.getUniqueId())){
                player.sendMessage(ChatColor.RED + "No pending teleport request from " + args[0] + ".");
                return true;
            }
        } else {
            request = plugin.getTeleportRequestToTarget(player.getUniqueId());
            if (request == null) {
                player.sendMessage(ChatColor.RED + "You have no pending teleport requests.");
                return true;
            }
        }

        if (request.hasExpired(teleportConfig.getTpaRequestTimeoutSeconds())) {
            player.sendMessage(ChatColor.RED + "This teleport request has expired.");
            plugin.removeTeleportRequest(request);
            return true;
        }

        Player requester = Bukkit.getPlayer(request.requesterUuid());
        Player target = Bukkit.getPlayer(request.targetUuid()); // Should be 'player'

        if (requester == null || !requester.isOnline()) {
            player.sendMessage(ChatColor.RED + request.requesterName() + " is no longer online.");
            plugin.removeTeleportRequest(request);
            return true;
        }
        if (target == null || !target.isOnline()) { // Should not happen if 'player' is the target
            requester.sendMessage(ChatColor.RED + request.targetName() + " is no longer online.");
             plugin.removeTeleportRequest(request);
            return true;
        }

        plugin.removeTeleportRequest(request); // Request is consumed

        Player playerToTeleport = request.toRequester() ? target : requester;
        Player destinationPlayer = request.toRequester() ? requester : target;
        Location destination = destinationPlayer.getLocation();

        player.sendMessage(ChatColor.GREEN + "Teleport request accepted from " + requester.getName() + ".");
        requester.sendMessage(ChatColor.GREEN + target.getName() + " accepted your teleport request.");

        // Teleport logic with delay
        int delay = teleportConfig.getTpaTeleportDelaySeconds();
        Map<UUID, BukkitTask> pendingTeleports = homeCommands.getPendingTeleports();

        if (delay > 0 && !playerToTeleport.hasPermission("diamondplugin.teleport.nomovepenalty")) {
            if (pendingTeleports.containsKey(playerToTeleport.getUniqueId())) {
                pendingTeleports.get(playerToTeleport.getUniqueId()).cancel();
            }
            playerToTeleport.sendMessage(ChatColor.GREEN + "Teleporting in " + delay + " seconds. Don't move!");
            if (!playerToTeleport.equals(destinationPlayer)) { // If the other player is also involved in message
                 destinationPlayer.sendMessage(ChatColor.GREEN + playerToTeleport.getName() + " will teleport in " + delay + " seconds.");
            }

            BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                playerToTeleport.teleport(destination);
                playerToTeleport.sendMessage(ChatColor.GREEN + "Teleported!");
                if (!playerToTeleport.equals(destinationPlayer)) {
                     destinationPlayer.sendMessage(ChatColor.GREEN + playerToTeleport.getName() + " has teleported to you!");
                }
                pendingTeleports.remove(playerToTeleport.getUniqueId());
                // Optional: Add TPA specific cooldown if desired
            }, delay * 20L);
            pendingTeleports.put(playerToTeleport.getUniqueId(), task);
        } else {
            playerToTeleport.teleport(destination);
            playerToTeleport.sendMessage(ChatColor.GREEN + "Teleported!");
             if (!playerToTeleport.equals(destinationPlayer)) {
                 destinationPlayer.sendMessage(ChatColor.GREEN + playerToTeleport.getName() + " has teleported " + (request.toRequester() ? "to you!" : "to them!"));
            }
            // Optional: Add TPA specific cooldown
        }
        return true;
    }

    private boolean handleTpDeny(Player player, String[] args) { // Player is the target
        if (!player.hasPermission("diamondplugin.command.tpdeny")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        TeleportRequest request;
         if (args.length > 0) {
            Player requester = Bukkit.getPlayer(args[0]);
            if (requester == null) {
                player.sendMessage(ChatColor.RED + "Player '" + args[0] + "' not found for denying request.");
                return true;
            }
            request = plugin.getTeleportRequestToTarget(player.getUniqueId());
             if(request == null || !request.requesterUuid().equals(requester.getUniqueId())){
                player.sendMessage(ChatColor.RED + "No pending teleport request from " + args[0] + " to deny.");
                return true;
            }
        } else {
            request = plugin.getTeleportRequestToTarget(player.getUniqueId());
            if (request == null) {
                player.sendMessage(ChatColor.RED + "You have no pending teleport requests to deny.");
                return true;
            }
        }

        plugin.removeTeleportRequest(request);
        player.sendMessage(ChatColor.YELLOW + "Teleport request from " + request.requesterName() + " denied.");
        Player requesterOnline = Bukkit.getPlayer(request.requesterUuid());
        if (requesterOnline != null) {
            requesterOnline.sendMessage(ChatColor.RED + player.getName() + " denied your teleport request.");
        }
        return true;
    }

    private boolean handleTpCancel(Player player) { // Player is the requester
        if (!player.hasPermission("diamondplugin.command.tpcancel")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        TeleportRequest request = plugin.getTeleportRequestFromRequester(player.getUniqueId());
        if (request == null) {
            player.sendMessage(ChatColor.RED + "You do not have any outgoing teleport requests to cancel.");
            return true;
        }

        plugin.removeTeleportRequest(request);
        player.sendMessage(ChatColor.YELLOW + "Your teleport request to " + request.targetName() + " has been cancelled.");
        Player targetOnline = Bukkit.getPlayer(request.targetUuid());
        if (targetOnline != null) {
            targetOnline.sendMessage(ChatColor.YELLOW + player.getName() + " cancelled their teleport request.");
        }
        return true;
    }


    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (!(sender instanceof Player)) return completions;
        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("tpa") || command.getName().equalsIgnoreCase("tpahere")) {
            if (args.length == 1) {
                Bukkit.getOnlinePlayers().stream()
                        .filter(p -> !p.equals(player) && p.getName().toLowerCase().startsWith(args[0].toLowerCase()))
                        .forEach(p -> completions.add(p.getName()));
            }
        } else if (command.getName().equalsIgnoreCase("tpaccept") || command.getName().equalsIgnoreCase("tpdeny")) {
            if (args.length == 1) {
                // Suggest players who have sent a request to the command sender
                TeleportRequest currentRequest = plugin.getTeleportRequestToTarget(player.getUniqueId());
                if(currentRequest != null && currentRequest.requesterName().toLowerCase().startsWith(args[0].toLowerCase())) {
                     completions.add(currentRequest.requesterName());
                }
                // In a system with multiple incoming requests, you'd iterate a list of them
            }
        }
        // No completions for /tpcancel
        return completions.stream().sorted().collect(Collectors.toList());
    }
}
