package com.stellinova.syncblade;

import com.example.evo.api.IEvoService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SyncBladePlugin extends JavaPlugin {

    private final Map<UUID, SyncPlayerData> data = new HashMap<>();

    private IEvoService evo;
    private SyncManager manager;
    private SyncScoreboardHud hud;

    @Override
    public void onEnable() {
        // Resolve Evo service if present
        try {
            evo = Bukkit.getServicesManager().load(IEvoService.class);
        } catch (Throwable ignored) {
            evo = null;
        }

        // Access bridge
        try { SyncAccessBridge.init(this); } catch (Throwable ignored) {}

        // Core systems
        manager = new SyncManager(this);
        hud = new SyncScoreboardHud(this);

        // Command: /syncblade
        try {
            if (getCommand("syncblade") != null) {
                getCommand("syncblade").setExecutor(new SyncCommand(this, manager, hud, evo));
            }
        } catch (Throwable ignored) {}

        // PlaceholderAPI expansion
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                new SyncExpansion(this).register();
            }
        } catch (Throwable ignored) {}

        // Prewarm for online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            data(p);
            try { SyncAccessBridge.warm(p); } catch (Throwable ignored) {}
            try { hud.refresh(p); } catch (Throwable ignored) {}
        }

        getLogger().info("SyncBlade v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        try { if (manager != null) manager.shutdown(); } catch (Throwable ignored) {}
        data.clear();
        getLogger().info("SyncBlade disabled.");
    }

    /** Per-player data accessor (public so manager/HUD/expansion can reuse). */
    public SyncPlayerData data(Player p) {
        return data.computeIfAbsent(p.getUniqueId(), id -> new SyncPlayerData(id));
    }

    public IEvoService getEvo() {
        return evo;
    }

    public void warmAccess(Player p) {
        try { SyncAccessBridge.warm(p); } catch (Throwable ignored) {}
    }

    /** Called when rune is revoked via /syncblade reset to immediately clear state visuals. */
    public void onRuneRevoked(Player p) {
        if (manager != null) manager.onRuneRevoked(p);
    }
}
