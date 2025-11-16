package com.stellinova.syncblade;

import com.example.evo.api.IEvoService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class SyncBladePlugin extends JavaPlugin {

    private SyncManager manager;
    private SyncScoreboardHud hud;
    private IEvoService evo;

    @Override
    public void onEnable() {
        // Resolve Evo service if present
        try {
            evo = Bukkit.getServicesManager().load(IEvoService.class);
        } catch (Throwable ignored) {
            evo = null;
        }

        // Access bridge (rune state)
        try {
            SyncAccessBridge.init(this);
        } catch (Throwable ignored) {}

        // Core manager
        manager = new SyncManager(this, evo);

        // HUD
        hud = new SyncScoreboardHud(this, manager, evo);

        // Command: /syncblade
        try {
            if (getCommand("syncblade") != null) {
                getCommand("syncblade").setExecutor(new SyncCommand(this, manager, hud, evo));
            }
        } catch (Throwable ignored) {}

        // PlaceholderAPI expansion (optional)
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                // SyncExpansion currently has constructor SyncBladePlugin only
                new SyncExpansion(this).register();
            }
        } catch (Throwable ignored) {}

        // First-time HUD build for online players
        Bukkit.getOnlinePlayers().forEach(p -> {
            try { hud.refresh(p); } catch (Throwable ignored) {}
        });

        getLogger().info("SyncBlade enabled.");
    }

    @Override
    public void onDisable() {
        try { if (manager != null) manager.shutdown(); } catch (Throwable ignored) {}
        try { if (hud != null) hud.shutdown(); } catch (Throwable ignored) {}
        getLogger().info("SyncBlade disabled.");
    }

    public SyncManager getManager() {
        return manager;
    }

    public SyncScoreboardHud getHud() {
        return hud;
    }

    public IEvoService getEvo() {
        return evo;
    }
}
