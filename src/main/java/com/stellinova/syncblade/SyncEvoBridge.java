package com.stellinova.syncblade;

import com.example.evo.api.IEvoService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Simple Evo bridge for SyncBlade.
 * Uses EF4's IEvoService directly, same style as your other runes.
 */
public final class SyncEvoBridge {

    private static IEvoService service;

    static {
        try {
            service = Bukkit.getServicesManager().load(IEvoService.class);
        } catch (Throwable ignored) {
            service = null;
        }
    }

    private SyncEvoBridge() {}

    /** EVO stage 0..3 from EvoCore. */
    public static int evo(Player p) {
        if (p == null || service == null) return 0;
        try {
            UUID id = p.getUniqueId();
            int lvl = service.getEvoLevel(id);
            if (lvl < 0) lvl = 0;
            if (lvl > 3) lvl = 3;
            return lvl;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /** Multiplier from EvoCore (defaults 1.0 if anything is off). */
    public static double m(Player p, String key) {
        if (p == null || service == null || key == null) return 1.0;
        try {
            int lvl = evo(p);
            double v = service.multiplier(key, lvl);
            if (!Double.isFinite(v) || v <= 0.0) return 1.0;
            return v;
        } catch (Throwable ignored) {
            return 1.0;
        }
    }
}
