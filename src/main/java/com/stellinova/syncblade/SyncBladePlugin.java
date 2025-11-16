package com.stellinova.syncblade;

import com.example.evo.api.IEvoService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SyncBlade main plugin — wired like Winder/Tide:
 *  - Loads IEvoService from EvoCore
 *  - Creates Manager + HUD with Evo
 *  - Cleans up on disable
 */
public class SyncBladePlugin extends JavaPlugin {

    private final Map<UUID, SyncPlayerData> data = new HashMap<>();

    private IEvoService evo;
    private SyncManager manager;
    private SyncScoreboardHud hud;

    @Override
    public void onEnable() {
        // Evo service (Winder/Tide style)
        try {
            evo = Bukkit.getServicesManager().load(IEvoService.class);
        } catch (Throwable ignored) {
            evo = null;
        }

        // Rune access bridge
        try {
            SyncAccessBridge.init(this);
        } catch (Throwable ignored) {}

        // Core manager + HUD
        manager = new SyncManager(this);
        hud = new SyncScoreboardHud(this, manager, evo);

        // Command: /syncblade
        if (getCommand("syncblade") != null) {
            getCommand("syncblade").setExecutor(new SyncCommand(this, manager, hud, evo));
        }

        // PlaceholderAPI expansion (optional)
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                new SyncExpansion(this, manager, evo).register();
            }
        } catch (Throwable ignored) {}

        // First-time HUD build for any online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            data(p); // warm data
            try {
                SyncAccessBridge.warm(p);
            } catch (Throwable ignored) {}
            try {
                hud.refresh(p);
            } catch (Throwable ignored) {}
        }

        getLogger().info("SyncBlade enabled.");
    }

    @Override
    public void onDisable() {
        try {
            if (manager != null) manager.shutdown();
        } catch (Throwable ignored) {}
        try {
            if (hud != null) hud.shutdown();
        } catch (Throwable ignored) {}
        data.clear();
        getLogger().info("SyncBlade disabled.");
    }

    public SyncPlayerData data(Player p) {
        return data.computeIfAbsent(p.getUniqueId(), id -> new SyncPlayerData(id));
    }

    public IEvoService getEvo() {
        return evo;
    }

    public void warmAccess(Player p) {
        try {
            SyncAccessBridge.warm(p);
        } catch (Throwable ignored) {}
    }

    public void onRuneRevoked(Player p) {
        if (manager != null) manager.onRuneRevoked(p);
    }
}
