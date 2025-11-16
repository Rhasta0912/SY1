package com.stellinova.syncblade;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;

/**
 * Evo bridge for Syncblade.
 * 1) Tries EvoCore service (both com.example.evo.IEvoService and com.example.evo.api.IEvoService).
 * 2) Fallback to PlaceholderAPI (%evo_*%).
 * 3) Else returns safe defaults.
 */
public final class SyncEvoBridge {

    public enum Link { SERVICE, PAPI, NONE }

    private static volatile Object service;
    private static volatile Class<?> serviceClass;
    private static volatile Link linkMode = Link.NONE;

    private SyncEvoBridge() {}

    static {
        tryHookService();
    }

    public static String linkMode() {
        return linkMode.name();
    }

    /** EVO stage 0..3 */
    public static int evo(Player p) {
        if (linkMode == Link.SERVICE && service != null) {
            try {
                Method m = findMethod("getEvo", Player.class);
                if (m != null) return (int) m.invoke(service, p);

                m = findMethod("getEvoLevel", UUID.class);
                if (m != null) return (int) m.invoke(service, p.getUniqueId());

                m = findMethod("getEvo", UUID.class);
                if (m != null) return (int) m.invoke(service, p.getUniqueId());
            } catch (Throwable ignored) {}
        }

        if (linkMode == Link.PAPI) {
            try {
                String s = PlaceholderAPI.setPlaceholders(p, "%evo_stage%");
                return Integer.parseInt(s.trim());
            } catch (Throwable ignored) {}
        }

        return 0;
    }

    /** Scalar multiplier (defaults 1.0) */
    public static double m(Player p, String key) {
        key = key.toLowerCase(Locale.ROOT);

        if (linkMode == Link.SERVICE && service != null) {
            try {
                Method m = findMethod("getScalar", Player.class, String.class);
                if (m != null) return toDouble(m.invoke(service, p, key));

                m = findMethod("getScalar", UUID.class, String.class);
                if (m != null) return toDouble(m.invoke(service, p.getUniqueId(), key));
            } catch (Throwable ignored) {}
        }

        if (linkMode == Link.PAPI) {
            try {
                String ph = switch (key) {
                    case "echo"      -> "%evo_mult_dash%";
                    case "reverb"    -> "%evo_mult_pull%";
                    case "crescendo" -> "%evo_mult_dive%";
                    default -> null;
                };
                if (ph != null) {
                    String s = PlaceholderAPI.setPlaceholders(p, ph);
                    return Double.parseDouble(s.trim());
                }
            } catch (Throwable ignored) {}
        }

        return 1.0;
    }

    // ---------------- internal helpers ----------------

    private static void tryHookService() {
        try {
            Class<?> apiClass = Class.forName("com.example.evo.api.IEvoService");
            Object svc = Bukkit.getServicesManager().load(apiClass);
            if (svc != null) {
                service = svc;
                serviceClass = apiClass;
                linkMode = Link.SERVICE;
                return;
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> coreClass = Class.forName("com.example.evo.IEvoService");
            Object svc = Bukkit.getServicesManager().load(coreClass);
            if (svc != null) {
                service = svc;
                serviceClass = coreClass;
                linkMode = Link.SERVICE;
                return;
            }
        } catch (Throwable ignored) {}

        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                linkMode = Link.PAPI;
                return;
            }
        } catch (Throwable ignored) {}

        linkMode = Link.NONE;
    }

    private static Method findMethod(String name, Class<?>... params) {
        if (serviceClass == null) return null;
        try {
            return serviceClass.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static double toDouble(Object o) {
        if (o == null) return 1.0;
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(o.toString().trim());
        } catch (Throwable ignored) {
            return 1.0;
        }
    }
}
