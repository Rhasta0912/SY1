// v0.2.0 — SyncBlade Evo bridge (service + PAPI + safe fallback)
package com.stellinova.syncblade;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Locale;

public final class SyncEvoBridge {

    private enum Link { SERVICE, PAPI, NONE }

    private static Link linkMode = Link.NONE;
    private static Class<?> svcClass;
    private static Object serviceInstance;
    private static Method mGetEvo;
    private static Method mGetScalar;

    private SyncEvoBridge() {}

    // ------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------

    /** EVO stage 0–3 for a player. */
    public static int evo(Player p) {
        ensureHooked();
        if (p == null) return 0;

        // 1) Direct EvoCore service (com.example.evo.IEvoService or .api.IEvoService)
        if (linkMode == Link.SERVICE) {
            try {
                Object res = mGetEvo.invoke(serviceInstance, p);
                if (res instanceof Number n) {
                    return clampInt(String.valueOf(n.intValue()), 0, 3);
                }
            } catch (Throwable ignored) {}
        }

        // 2) PlaceholderAPI (%evo_stage%)
        if (linkMode == Link.PAPI) {
            try {
                String s = PlaceholderAPI.setPlaceholders(p, "%evo_stage%");
                return clampInt(s, 0, 3);
            } catch (Throwable ignored) {}
        }

        // 3) Nothing hooked → fallback 0
        return 0;
    }

    /**
     * Multiplier for SyncBlade-specific keys.
     * Keys: echo, reverb, crescendo, rhythm.
     * Returns 1.0 if EvoCore/PAPI not available.
     */
    public static double m(Player p, String key) {
        ensureHooked();
        if (p != null && linkMode == Link.SERVICE) {
            try {
                Object res = mGetScalar.invoke(serviceInstance, p, key.toLowerCase(Locale.ROOT));
                if (res instanceof Number n) return n.doubleValue();
            } catch (Throwable ignored) {}
        }

        if (p != null && linkMode == Link.PAPI) {
            try {
                String place = switch (key.toLowerCase(Locale.ROOT)) {
                    case "echo"      -> "%evo_mult_dash%";
                    case "reverb"    -> "%evo_mult_pull%";
                    case "crescendo" -> "%evo_mult_dive%";
                    case "rhythm"    -> "%evo_mult_jump%";
                    default          -> "%evo_mult_dash%";
                };
                String s = PlaceholderAPI.setPlaceholders(p, place);
                return Double.parseDouble(s.trim());
            } catch (Throwable ignored) {}
        }

        // Fallback math if no EvoCore/PAPI – scale by evo level
        int lvl = (p == null ? 0 : evo(p));
        double bonus = switch (key.toLowerCase(Locale.ROOT)) {
            case "echo"      -> 0.20;
            case "reverb"    -> 0.22;
            case "crescendo" -> 0.30;
            case "rhythm"    -> 0.18;
            default          -> 0.15;
        };
        return 1.0 + bonus * Math.max(0, Math.min(3, lvl));
    }

    // ------------------------------------------------------------
    // Hooking logic (service / PAPI)
    // ------------------------------------------------------------

    private static void ensureHooked() {
        if (linkMode != Link.NONE) return;

        // 1) Try EvoCore service – real one is com.example.evo.IEvoService
        if (tryLoadServiceClass("com.example.evo.IEvoService")) return;
        if (tryLoadServiceClass("com.example.evo.api.IEvoService")) return;

        // 2) Try PlaceholderAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            linkMode = Link.PAPI;
            return;
        }

        // 3) Nothing
        linkMode = Link.NONE;
    }

    private static boolean tryLoadServiceClass(String name) {
        try {
            Class<?> clazz = Class.forName(name);
            Object svc = Bukkit.getServicesManager().load(clazz);
            if (svc == null) return false;

            Method getEvo = findMethod(clazz, "getEvo", org.bukkit.entity.Player.class);
            if (getEvo == null) getEvo = findMethod(clazz, "getEvoLevel", java.util.UUID.class);
            if (getEvo == null) return false;

            Method getScalar = findMethod(clazz, "getScalar", org.bukkit.entity.Player.class, String.class);
            if (getScalar == null) return false;

            svcClass = clazz;
            serviceInstance = svc;
            mGetEvo = getEvo;
            mGetScalar = getScalar;
            linkMode = Link.SERVICE;
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... params) {
        try {
            return clazz.getMethod(name, params);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int clampInt(String s, int min, int max) {
        try {
            int v = Integer.parseInt(s.trim());
            return Math.max(min, Math.min(max, v));
        } catch (Throwable ignored) {
            return min;
        }
    }
}
