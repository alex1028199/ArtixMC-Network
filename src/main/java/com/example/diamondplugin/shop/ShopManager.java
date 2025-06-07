package com.example.diamondplugin.shop;

import com.example.diamondplugin.DiamondPlugin;
import com.example.diamondplugin.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.NamespacedKey;


import java.util.ArrayList;
import java.util.Collections; // Added import
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class ShopManager {

    private final DiamondPlugin plugin;
    private final Map<String, Shop> shops = new HashMap<>();
    private final EconomyManager economyManager;

    // PersistentDataContainer keys
    public final NamespacedKey SHOP_ITEM_KEY;
    public final NamespacedKey SHOP_NAME_KEY;
    public final NamespacedKey SHOP_ITEM_CONFIG_KEY;


    public ShopManager(DiamondPlugin plugin) {
        this.plugin = plugin;
        this.economyManager = plugin.getEconomyManager();

        // Initialize NamespacedKeys with the plugin instance
        SHOP_ITEM_KEY = new NamespacedKey(plugin, "shop_item_identifier");
        SHOP_NAME_KEY = new NamespacedKey(plugin, "shop_name");
        SHOP_ITEM_CONFIG_KEY = new NamespacedKey(plugin, "shop_item_config_key");

        loadShops();
    }

    public void loadShops() {
        shops.clear();
        ConfigurationSection shopsSection = plugin.getShopsConfig().getConfigurationSection("shops");
        if (shopsSection == null) {
            plugin.getLogger().warning("No 'shops' section found in shops.yml. No shops will be loaded.");
            return;
        }

        for (String shopKey : shopsSection.getKeys(false)) {
            ConfigurationSection shopConfig = shopsSection.getConfigurationSection(shopKey);
            if (shopConfig == null) continue;

            String title = ChatColor.translateAlternateColorCodes('&', shopConfig.getString("title", "&cUnnamed Shop"));
            int size = shopConfig.getInt("size", 27);
            if (size % 9 != 0 || size <= 0 || size > 54) {
                plugin.getLogger().warning("Invalid size " + size + " for shop '" + shopKey + "'. Defaulting to 27.");
                size = 27;
            }

            Map<Integer, ShopItem> items = new HashMap<>();
            ConfigurationSection itemsSection = shopConfig.getConfigurationSection("items");
            if (itemsSection != null) {
                for (String slotKey : itemsSection.getKeys(false)) {
                    try {
                        int slot = Integer.parseInt(slotKey);
                        if (slot < 0 || slot >= size) {
                            plugin.getLogger().warning("Invalid slot '" + slotKey + "' for shop '" + shopKey + "'. Skipping.");
                            continue;
                        }

                        ConfigurationSection itemConfig = itemsSection.getConfigurationSection(slotKey);
                        if (itemConfig == null) continue;

                        Material material = Material.matchMaterial(itemConfig.getString("material", ""));
                        if (material == null || material == Material.AIR) {
                            plugin.getLogger().warning("Invalid material for item in slot " + slot + " for shop '" + shopKey + "'. Skipping.");
                            continue;
                        }

                        ShopItem shopItem = new ShopItem(
                                material,
                                itemConfig.getInt("data", 0), // Legacy data value
                                itemConfig.getDouble("buy_price", -1),
                                itemConfig.getDouble("sell_price", -1),
                                itemConfig.getInt("quantity", 1),
                                itemConfig.getString("display_name"),
                                itemConfig.getStringList("lore")
                        );
                        items.put(slot, shopItem);
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Invalid slot key '" + slotKey + "' (must be a number) in shop '" + shopKey + "'. Skipping.");
                    } catch (IllegalArgumentException e) {
                         plugin.getLogger().warning("Error loading item in slot " + slotKey + " for shop '" + shopKey + "': " + e.getMessage());
                    }
                }
            }
            shops.put(shopKey.toLowerCase(), new Shop(shopKey, title, size, items));
            plugin.getLogger().info("Loaded shop: " + shopKey);
        }
    }

    public Shop getShop(String shopName) {
        return shops.get(shopName.toLowerCase());
    }

    public Map<String, Shop> getAllShops() { // Added for ShopListener and potentially /shop list
        return Collections.unmodifiableMap(shops);
    }

    public void openShop(Player player, String shopName) {
        Shop shop = getShop(shopName);
        if (shop == null) {
            player.sendMessage(ChatColor.RED + "Shop '" + shopName + "' not found.");
            return;
        }

        Inventory shopInventory = Bukkit.createInventory(null, shop.size(), shop.title());

        // Store shop name in inventory's PDC for later identification in listener
        // Note: Bukkit.createInventory doesn't directly support PDC on the inventory itself easily without NMS or custom holders.
        // We will primarily rely on item PDCs and inventory title for identification.

        for (Map.Entry<Integer, ShopItem> entry : shop.items().entrySet()) {
            int slot = entry.getKey();
            ShopItem shopItemData = entry.getValue();
            ItemStack itemStack = new ItemStack(shopItemData.material(), shopItemData.quantity());

            // For 1.13+ data values are part of material names or specific item types (e.g. PotionMeta)
            // The 'data' field is mostly for legacy or very specific cases.

            ItemMeta meta = itemStack.getItemMeta();
            if (meta != null) {
                if (shopItemData.displayName() != null && !shopItemData.displayName().isEmpty()) {
                    meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', shopItemData.displayName()));
                }

                List<String> lore = new ArrayList<>();
                if (shopItemData.lore() != null) {
                    for (String line : shopItemData.lore()) {
                        String buyPriceFormatted = shopItemData.buyPrice() != -1 ? economyManager.format(shopItemData.buyPrice()) : "N/A";
                        String sellPriceFormatted = shopItemData.sellPrice() != -1 ? economyManager.format(shopItemData.sellPrice()) : "N/A";
                        lore.add(ChatColor.translateAlternateColorCodes('&',
                                line.replace("{buy_price}", String.valueOf(shopItemData.buyPrice()))
                                    .replace("{sell_price}", String.valueOf(shopItemData.sellPrice()))
                                    .replace("{buy_price_formatted}", buyPriceFormatted)
                                    .replace("{sell_price_formatted}", sellPriceFormatted)
                        ));
                    }
                }
                // Add price info to lore if not already present by user's custom lore
                boolean hasBuyInfo = lore.stream().anyMatch(l -> l.toLowerCase().contains("buy for"));
                boolean hasSellInfo = lore.stream().anyMatch(l -> l.toLowerCase().contains("sell for"));

                if (shopItemData.buyPrice() != -1 && !hasBuyInfo) {
                    lore.add(ChatColor.GREEN + "Buy for: " + economyManager.format(shopItemData.buyPrice() * shopItemData.quantity()));
                }
                if (shopItemData.sellPrice() != -1 && !hasSellInfo) {
                    lore.add(ChatColor.RED + "Sell for: " + economyManager.format(shopItemData.sellPrice() * shopItemData.quantity()));
                }
                 if(shopItemData.quantity() > 1 && (shopItemData.buyPrice() != -1 || shopItemData.sellPrice() != -1)){
                    lore.add(ChatColor.DARK_GRAY + "(Price for " + shopItemData.quantity() + " items)");
                }


                meta.setLore(lore);

                // Persistent Data Container for shop identification
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                pdc.set(SHOP_ITEM_KEY, PersistentDataType.STRING, "true"); // Mark as a shop item
                pdc.set(SHOP_NAME_KEY, PersistentDataType.STRING, shop.shopName()); // Store which shop it belongs to
                pdc.set(SHOP_ITEM_CONFIG_KEY, PersistentDataType.INTEGER, slot); // Store its original config slot key

                itemStack.setItemMeta(meta);
            }
            shopInventory.setItem(slot, itemStack);
        }
        player.openInventory(shopInventory);
    }
}
