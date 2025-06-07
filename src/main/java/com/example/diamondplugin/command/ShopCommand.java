package com.example.diamondplugin.command;

import com.example.diamondplugin.DiamondPlugin;
import com.example.diamondplugin.shop.Shop;
import com.example.diamondplugin.shop.ShopManager;
import org.bukkit.ChatColor;
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
import java.util.Map;
import java.util.stream.Collectors;

public class ShopCommand implements CommandExecutor, TabCompleter {

    private final DiamondPlugin plugin;
    private final ShopManager shopManager;

    public ShopCommand(DiamondPlugin plugin) {
        this.plugin = plugin;
        this.shopManager = plugin.getShopManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        Player player = (Player) sender;

        if (!player.hasPermission("diamondplugin.command.shop")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        Map<String, Shop> availableShops = shopManager.getAllShops();

        if (args.length == 0) {
            if (availableShops.isEmpty()) {
                player.sendMessage(ChatColor.RED + "No shops are currently available.");
                return true;
            } else if (availableShops.size() == 1) {
                // Open the only available shop
                shopManager.openShop(player, availableShops.keySet().iterator().next());
            } else {
                player.sendMessage(ChatColor.YELLOW + "Available shops: " + String.join(", ", availableShops.keySet()));
                player.sendMessage(ChatColor.YELLOW + "Usage: /shop <shop_name>");
            }
            return true;
        }

        String shopName = args[0];
        Shop shop = shopManager.getShop(shopName);
        if (shop == null) {
            player.sendMessage(ChatColor.RED + "Shop '" + shopName + "' not found.");
            player.sendMessage(ChatColor.YELLOW + "Available shops: " + String.join(", ", availableShops.keySet()));
            return true;
        }

        shopManager.openShop(player, shopName);
        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return shopManager.getAllShops().keySet().stream()
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .sorted()
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
