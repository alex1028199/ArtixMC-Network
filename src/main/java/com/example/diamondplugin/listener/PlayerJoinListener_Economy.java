package com.example.diamondplugin.listener;

import com.example.diamondplugin.DiamondPlugin;
import com.example.diamondplugin.economy.EconomyManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener_Economy implements Listener {

    private final DiamondPlugin plugin;
    private final EconomyManager economyManager;

    public PlayerJoinListener_Economy(DiamondPlugin plugin) {
        this.plugin = plugin;
        this.economyManager = plugin.getEconomyManager();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        java.util.UUID playerUuid = player.getUniqueId();

        if (economyManager != null && !economyManager.hasAccount(playerUuid)) {
            economyManager.createPlayerAccount(playerUuid, plugin.getEconomyConfig().getStartingBalance(), true); // save immediately on join
            plugin.getLogger().info("Created economy account for " + player.getName() + " with starting balance: " + plugin.getEconomyConfig().getStartingBalance());
        }
    }
}
