package com.example.diamondplugin.shop;

import java.util.Map;

public record Shop(
        String shopName, // The key from shops.yml (e.g., "admin_shop")
        String title,
        int size,
        Map<Integer, ShopItem> items // Key is the slot index
) {
}
