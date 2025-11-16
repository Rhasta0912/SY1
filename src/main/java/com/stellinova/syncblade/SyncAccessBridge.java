package com.stellinova.syncblade;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central toggle for whether a player can use the SyncBlade rune.
 * Persists via PDC and maintains a fast in-memory cache.
 */
public final class SyncAccessBridge {

    private static JavaPlugin plugin;
    private static NamespacedKey keyEnabled;

    private static final Set<UUID> ENABLED = ConcurrentHashMap.newKeySet();

    private SyncAccessBridge() {}

    public static void init(JavaPlugin pl) {
        plugin = pl;
        keyEnabled = new NamespacedKey(plugin, "syncblade_enabled");
    }

    public static boolean canUseSync(Player p) {
        if (ENABLED.contains(p.getUniqueId())) return true;

        PersistentDataContainer pdc = p.getPersistentDataContainer();
        Integer val = pdc.get(keyEnabled, PersistentDataType.INTEGER);
        boolean enabled = (val != null && val == 1);
        if (enabled) ENABLED.add(p.getUniqueId());
        return enabled;
    }

    public static boolean isSync(Player p) {
        return canUseSync(p);
    }

    public static void grant(Player p) {
        setState(p, true);
    }

    public static void revoke(Player p) {
        setState(p, false);
    }

    private static void setState(Player p, boolean enabled) {
        if (plugin == null || keyEnabled == null) {
            throw new IllegalStateException("SyncAccessBridge.init(plugin) was not called");
        }

        PersistentDataContainer pdc = p.getPersistentDataContainer();
        if (enabled) {
            pdc.set(keyEnabled, PersistentDataType.INTEGER, 1);
            ENABLED.add(p.getUniqueId());
        } else {
            pdc.set(keyEnabled, PersistentDataType.INTEGER, 0);
            ENABLED.remove(p.getUniqueId());
        }
    }

    public static void warm(Player p) {
        if (canUseSync(p)) ENABLED.add(p.getUniqueId());
        else ENABLED.remove(p.getUniqueId());
    }
}
