package com.stellinova.syncblade;

import com.example.evo.api.IEvoService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Simple Evo bridge for SyncBlade:
 *  - Prefer EvoCore service (IEvoService)
 *  - If not present, fall back to Evo 0 and multipliers of 1.0
 */
public final class SyncEvoBridge {

    private static IEvoService service;
    private static boolean triedHook = false;

    private SyncEvoBridge() {}

    private static void ensureHooked() {
        if (triedHook) return;
        triedHook = true;
        try {
            service = Bukkit.getServicesManager().load(IEvoService.class);
        } catch (Throwable ignored) {
            service = null;
        }
    }

    /** EVO stage 0..3. */
    public static int evo(Player p) {
        ensureHooked();
        if (service == null || p == null) return 0;
        try {
            int lvl = service.getEvoLevel(p.getUniqueId());
            if (lvl < 0) lvl = 0;
            if (lvl > 3) lvl = 3;
            return lvl;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /**
     * Scalar multiplier: uses EvoCore's multiplier(key, level) if available.
     * If not, falls back to 1.0 so gameplay still works.
     */
    public static double m(Player p, String key) {
        ensureHooked();
        if (service == null || p == null || key == null) return 1.0;
        int lvl = evo(p);
        try {
            double val = service.multiplier(key, lvl);
            if (!Double.isFinite(val) || val <= 0.0) return 1.0;
            return val;
        } catch (Throwable ignored) {
            return 1.0;
        }
    }
}
