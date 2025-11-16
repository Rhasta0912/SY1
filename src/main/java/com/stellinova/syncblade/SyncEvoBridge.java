package com.stellinova.syncblade;

import com.example.evo.api.IEvoService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Simple Evo bridge for SyncBlade.
 * Uses EvoCore's IEvoService via ServicesManager on each call
 * so load order never breaks EVO (unlike static init).
 */
public final class SyncEvoBridge {

    private SyncEvoBridge() {}

    private static IEvoService svc() {
        try {
            return Bukkit.getServicesManager().load(IEvoService.class);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** EVO stage 0..3 from EvoCore. */
    public static int evo(Player p) {
        if (p == null) return 0;
        IEvoService s = svc();
        if (s == null) return 0;
        try {
            UUID id = p.getUniqueId();
            int lvl = s.getEvoLevel(id);
            if (lvl < 0) lvl = 0;
            if (lvl > 3) lvl = 3;
            return lvl;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /** Multiplier from EvoCore (defaults 1.0 if anything is off). */
    public static double m(Player p, String key) {
        if (p == null || key == null) return 1.0;
        IEvoService s = svc();
        if (s == null) return 1.0;
        try {
            int lvl = evo(p);
            double v = s.multiplier(key, lvl);
            if (!Double.isFinite(v) || v <= 0.0) return 1.0;
            return v;
        } catch (Throwable ignored) {
            return 1.0;
        }
    }
}
