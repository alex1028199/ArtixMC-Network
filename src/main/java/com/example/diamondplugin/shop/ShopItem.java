package com.example.diamondplugin.shop;

import org.bukkit.Material;
import java.util.List;

public record ShopItem(
        Material material,
        int data, // For legacy items or specific variants, defaults to 0 if not specified
        double buyPrice, // -1 if not buyable
        double sellPrice, // -1 if not sellable
        int quantity, // Defaults to 1 if not specified
        String displayName, // Optional
        List<String> lore // Optional
) {
    // Constructor with defaults for optional fields
    public ShopItem {
        if (quantity <= 0) quantity = 1;
        if (buyPrice < 0 && buyPrice != -1) buyPrice = -1; // Normalize invalid negative price to -1 (not for sale)
        if (sellPrice < 0 && sellPrice != -1) sellPrice = -1; // Normalize invalid negative price to -1 (not buyable from player)
    }
}
