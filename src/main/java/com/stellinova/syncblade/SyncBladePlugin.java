package com.stellinova.syncblade;

import com.example.evo.api.IEvoService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SyncBlade main plugin.
 *
 * Provides:
 *  - Evo service lookup
 *  - Manager + HUD wiring
 *  - Shared per-player data accessor
 *  - Rune access warming + revoke hook
 */
public class SyncBladePlugin extends JavaPlugin {

    // Per-player runtime state
    private final Map<UUID, SyncPlayerData> data = new HashMap<>();

    private IEvoService evo;
    private SyncManager manager;
    private SyncScoreboardHud hud;

    @Override
    public void onEnable() {
        // EvoCore service (optional, used by bridge + commands)
        try {
            evo = Bukkit.getServicesManager().load(IEvoService.class);
        } catch (Throwable ignored) {
            evo = null;
        }

        // Rune access bridge
        try {
            SyncAccessBridge.init(this);
        } catch (Throwable ignored) {}

        // Core systems
        manager = new SyncManager(this);
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
                new SyncExpansion(this).register();
            }
        } catch (Throwable ignored) {}

        // Warm existing players (e.g., /reload)
        for (Player p : Bukkit.getOnlinePlayers()) {
            data(p);                     // ensure data exists
            try { SyncAccessBridge.warm(p); } catch (Throwable ignored) {}
            try { hud.refresh(p); }      catch (Throwable ignored) {}
        }

        getLogger().info("SyncBlade enabled.");
    }

    @Override
    public void onDisable() {
        try { if (manager != null) manager.shutdown(); } catch (Throwable ignored) {}
        try { if (hud != null) hud.shutdown(); }         catch (Throwable ignored) {}
        data.clear();
        getLogger().info("SyncBlade disabled.");
    }

    /**
     * Shared per-player data, used by manager / HUD / expansion / commands.
     */
    public SyncPlayerData data(Player p) {
        return data.computeIfAbsent(p.getUniqueId(), id -> new SyncPlayerData(id));
    }

    public IEvoService getEvo() {
        return evo;
    }

    /**
     * Called when a player gains rune access (e.g., /syncblade rune)
     * so any PDC/cache hooks can warm up.
     */
    public void warmAccess(Player p) {
        try {
            SyncAccessBridge.warm(p);
        } catch (Throwable ignored) {}
    }

    /**
     * Called when rune is revoked (e.g., /syncblade reset)
     * so manager can clear ability state.
     */
    public void onRuneRevoked(Player p) {
        if (manager != null) {
            manager.onRuneRevoked(p);
        }
    }
}
