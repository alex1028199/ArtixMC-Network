package com.example.diamondplugin.listener;

import com.example.diamondplugin.DiamondPlugin;
import com.example.diamondplugin.economy.EconomyManager;
import com.example.diamondplugin.shop.Shop;
import com.example.diamondplugin.shop.ShopItem;
import com.example.diamondplugin.shop.ShopManager;
import net.milkbowl.vault.economy.EconomyResponse; // Though not directly used, good for context if expanding
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey; // Already imported in ShopManager, but good practice if used directly
import org.bukkit.Bukkit; // Added for Bukkit.getOnlinePlayers() if that was intended for spy etc.

import java.util.ArrayList;
import java.util.HashMap; // Not used directly in this version, but often useful
import java.util.List; // THE MISSING IMPORT
import java.util.Map; // Not used directly in this version
import java.util.stream.Collectors;


public class ShopListener implements Listener {

    private final DiamondPlugin plugin;
    private final ShopManager shopManager;
    private final EconomyManager economyManager;

    public ShopListener(DiamondPlugin plugin) {
        this.plugin = plugin;
        this.shopManager = plugin.getShopManager();
        this.economyManager = plugin.getEconomyManager();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        Inventory clickedInventory = event.getClickedInventory();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedInventory == null || clickedItem == null || clickedItem.getType() == Material.AIR) return;

        String inventoryTitle = event.getView().getTitle();
        Shop activeShop = null;
        // Iterate over known shops to find a match by title.
        // ShopManager.getAllShops() was added for this.
        if (shopManager.getAllShops() != null) { // Ensure shopManager and its map are initialized
            for(Shop shop : shopManager.getAllShops().values()){
                if(shop.title().equals(inventoryTitle)){
                    activeShop = shop;
                    break;
                }
            }
        }

        // Fallback/Primary check using PDC on the item itself
        if(activeShop == null && clickedItem.hasItemMeta()){
             ItemMeta meta = clickedItem.getItemMeta();
             if (meta != null) { // Check meta not null
                 PersistentDataContainer pdc = meta.getPersistentDataContainer();
                 if(pdc.has(shopManager.SHOP_ITEM_KEY, PersistentDataType.STRING)){
                     String shopNameFromItem = pdc.get(shopManager.SHOP_NAME_KEY, PersistentDataType.STRING);
                     if(shopNameFromItem != null) activeShop = shopManager.getShop(shopNameFromItem);
                 }
             }
        }

        if (activeShop == null) return;

        event.setCancelled(true);

        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        if (!pdc.has(shopManager.SHOP_ITEM_KEY, PersistentDataType.STRING) ||
            !pdc.has(shopManager.SHOP_NAME_KEY, PersistentDataType.STRING) ||
            !pdc.has(shopManager.SHOP_ITEM_CONFIG_KEY, PersistentDataType.INTEGER)) {
            return;
        }

        String shopName = pdc.get(shopManager.SHOP_NAME_KEY, PersistentDataType.STRING);
        Integer itemSlotKey = pdc.get(shopManager.SHOP_ITEM_CONFIG_KEY, PersistentDataType.INTEGER);

        if (shopName == null || itemSlotKey == null) return;

        Shop currentShop = shopManager.getShop(shopName);
        if(currentShop == null) return;

        ShopItem shopItemData = currentShop.items().get(itemSlotKey);
        if (shopItemData == null) {
            player.sendMessage(ChatColor.RED + "Error: This shop item is no longer configured correctly.");
            return;
        }

        int quantityToTransact = shopItemData.quantity();
        boolean shiftClick = event.isShiftClick();


        if (event.isLeftClick()) { // Buy
            if (shopItemData.buyPrice() <= 0) {
                player.sendMessage(ChatColor.RED + "This item cannot be bought.");
                return;
            }

            double totalBuyPrice = shopItemData.buyPrice() * quantityToTransact;
            // Shift-click to buy a full stack - simplified for now
            if(shiftClick) {
                // For simplicity, shift-click buys one unit like normal left click.
                // True shift-buy-stack would involve calculating max affordable/fittable.
            }

            if (economyManager.getBalance(player.getUniqueId()) >= totalBuyPrice) {
                // Using Vault's EconomyResponse style for consistency, though our EconomyManager returns boolean
                if (economyManager.withdrawPlayer(player.getUniqueId(), totalBuyPrice)) {
                    ItemStack toGive = new ItemStack(shopItemData.material(), quantityToTransact);
                    player.getInventory().addItem(toGive);
                    player.sendMessage(ChatColor.GREEN + "You bought " + quantityToTransact + "x " + getItemName(shopItemData, toGive) + " for " + economyManager.format(totalBuyPrice) + ".");
                    economyManager.saveBalances();
                } else {
                    player.sendMessage(ChatColor.RED + "Transaction failed unexpectedly."); // Should be caught by getBalance check
                }
            } else {
                player.sendMessage(ChatColor.RED + "You don't have enough money to buy this. (" + economyManager.format(totalBuyPrice) + ")");
            }

        } else if (event.isRightClick()) { // Sell
            if (shopItemData.sellPrice() <= 0) {
                player.sendMessage(ChatColor.RED + "This item cannot be sold here.");
                return;
            }

            int itemsPlayerHas = countPlayerItems(player, shopItemData.material(), shopItemData.displayName(), shopItemData.lore());
            int itemsToSell = 0;

            if(shiftClick){
                itemsToSell = itemsPlayerHas; // Sell all matching items
            } else {
                itemsToSell = quantityToTransact; // Sell one configured unit
            }

            if (itemsToSell == 0 || itemsPlayerHas < itemsToSell) { // Check if they have any, or enough for a single transaction unit
                 player.sendMessage(ChatColor.RED + "You don't have " + (shiftClick ? "" : (quantityToTransact + "x ")) + getItemName(shopItemData, new ItemStack(shopItemData.material())) + " to sell.");
                 return;
            }

            // If not shift-clicking, ensure they have at least the configured quantity to sell
            if(!shiftClick && itemsPlayerHas < quantityToTransact) {
                 player.sendMessage(ChatColor.RED + "You need at least " + quantityToTransact + " of " + getItemName(shopItemData, new ItemStack(shopItemData.material())) + " to sell.");
                 return;
            }

            // Ensure itemsToSell does not exceed what the player has
            itemsToSell = Math.min(itemsToSell, itemsPlayerHas);


            if (removePlayerItems(player, shopItemData.material(), shopItemData.displayName(), shopItemData.lore(), itemsToSell)) {
                double totalSellPrice = shopItemData.sellPrice() * itemsToSell;
                economyManager.depositPlayer(player.getUniqueId(), totalSellPrice);
                player.sendMessage(ChatColor.GREEN + "You sold " + itemsToSell + "x " + getItemName(shopItemData, new ItemStack(shopItemData.material())) + " for " + economyManager.format(totalSellPrice) + ".");
                economyManager.saveBalances();
            } else {
                // This should ideally not be reached if countPlayerItems and item removal are robust
                player.sendMessage(ChatColor.RED + "Could not remove " + itemsToSell + " of " + getItemName(shopItemData, new ItemStack(shopItemData.material())) + " from your inventory.");
            }
        }
    }

    private String getItemName(ShopItem shopItemData, ItemStack itemStack){
        if(shopItemData.displayName() != null && !shopItemData.displayName().isEmpty()){
            return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', shopItemData.displayName()));
        }
        // Simple name formatting
        String name = itemStack.getType().toString().toLowerCase().replace("_", " ");
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    private int countPlayerItems(Player player, Material material, String displayName, List<String> lore) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                // For now, only matching material for selling.
                // Stricter matching would involve checking display name & lore if defined in shop.
                // if (shopItemData.displayName() != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                //     if (!ChatColor.stripColor(item.getItemMeta().getDisplayName()).equals(ChatColor.stripColor(displayName))) continue;
                // } else if (shopItemData.displayName() != null) continue; // Shop item has name, inventory item doesn't
                // Add lore checks similarly if strictness is required
                count += item.getAmount();
            }
        }
        return count;
    }

    private boolean removePlayerItems(Player player, Material material, String displayName, List<String> lore, int amountToRemove) {
        // Basic material removal. For stricter item matching, iterate and check meta.
        if (countPlayerItems(player, material, displayName, lore) < amountToRemove) {
            return false;
        }
        player.getInventory().removeItem(new ItemStack(material, amountToRemove));
        return true;
    }
}
