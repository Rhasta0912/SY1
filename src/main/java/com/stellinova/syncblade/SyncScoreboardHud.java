package com.stellinova.syncblade;

import com.example.evo.api.IEvoService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

/**
 * SyncBlade HUD — cloned infra from Tide/Winder:
 *  - Dedicated loop task (every 10t)
 *  - Hides only if our "synchud" owns SIDEBAR
 *  - EVO + per-ability bonuses as +%
 */
public class SyncScoreboardHud {

    private final SyncBladePlugin plugin;
    @SuppressWarnings("unused")
    private final SyncManager manager;
    @SuppressWarnings("unused")
    private final IEvoService evo; // reserved for direct EvoCore use, like Winder/Tide

    private BukkitTask loop;

    public SyncScoreboardHud(SyncBladePlugin plugin, SyncManager manager, IEvoService evo) {
        this.plugin = plugin;
        this.manager = manager;
        this.evo = evo;
        startLoop();
    }

    public void shutdown() {
        try {
            if (loop != null) {
                loop.cancel();
                loop = null;
            }
        } catch (Throwable ignored) {}
    }

    private void startLoop() {
        shutdown();
        loop = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    try {
                        refresh(p);
                    } catch (Throwable ignored) {}
                }
            }
        }.runTaskTimer(plugin, 1L, 10L); // ~0.5s just like Tide/Winder
    }

    public void refresh(Player p) {
        ScoreboardManager sm = Bukkit.getScoreboardManager();
        if (sm == null) return;

        // Hide HUD if they don't currently have SyncBlade
        if (!SyncAccessBridge.canUseSync(p)) {
            Scoreboard current = p.getScoreboard();
            Objective existing = current.getObjective("synchud");
            if (existing != null && existing.getDisplaySlot() == DisplaySlot.SIDEBAR) {
                Scoreboard empty = sm.getNewScoreboard();
                p.setScoreboard(empty);
            }
            return;
        }

        Scoreboard sb = sm.getNewScoreboard();
        Objective obj = sb.registerNewObjective(
                "synchud",
                Criteria.DUMMY,
                ChatColor.DARK_PURPLE + "SyncBlade"
        );
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        SyncPlayerData d = plugin.data(p);
        long now = System.currentTimeMillis();
        int evoLvl = Math.max(0, Math.min(3, SyncEvoBridge.evo(p)));

        long echoLeft  = Math.max(0L, d.getEchoReadyAt()       - now);
        long reverbLeft= Math.max(0L, d.getReverbReadyAt()     - now);
        long crescLeft = Math.max(0L, d.getCrescendoReadyAt()  - now);

        int score = 12;

        // EVO line
        add(obj, ChatColor.LIGHT_PURPLE + "EVO: " + ChatColor.AQUA + evoLvl, score--);

        // Echo Step (main burst)
        add(obj, abilityHeader("Echo Step", echoLeft), score--);
        add(obj, statLine("Burst", percent(abilityBonusPct(evoLvl, "echo"))), score--);

        // Reverb Strike (echo window)
        add(obj, abilityHeader("Reverb Strike", reverbLeft), score--);
        add(obj, statLine("Echo", percent(abilityBonusPct(evoLvl, "reverb"))), score--);

        // Rhythm Flow (passive)
        add(obj, ChatColor.DARK_PURPLE + "Rhythm Flow", score--);
        add(obj, rhythmLine(d.getRhythmStacks(), evoLvl), score--);

        // spacer
        add(obj, ChatColor.GRAY + "", score--);

        // Crescendo ultimate
        add(obj, crescendoLine(evoLvl, crescLeft), score--);

        p.setScoreboard(sb);
    }

    // ---------- helpers ----------

    private static void add(Objective obj, String text, int score) {
        String line = trim(text);
        while (obj.getScoreboard().getEntries().contains(line)) {
            line += ChatColor.RESET;
        }
        obj.getScore(line).setScore(score);
    }

    private static String abilityHeader(String name, long msLeft) {
        if (msLeft <= 0) return ChatColor.LIGHT_PURPLE + name;
        int sec = (int) Math.ceil(msLeft / 1000.0);
        return ChatColor.RED + name + ChatColor.GRAY + " (" + ChatColor.YELLOW + sec + "s" + ChatColor.GRAY + ")";
    }

    private static String statLine(String label, String value) {
        return ChatColor.AQUA + "  " + label + ChatColor.WHITE + ": " + value;
    }

    private static String rhythmLine(int stacks, int evoLvl) {
        // Slight flair so higher EVO feels better visually
        String bar = switch (Math.min(stacks, 10)) {
            case 0 -> ChatColor.DARK_GRAY + "-";
            case 1,2 -> ChatColor.GRAY + "▌";
            case 3,4 -> ChatColor.AQUA + "▋▋";
            case 5,6 -> ChatColor.LIGHT_PURPLE + "▋▋▋";
            default -> ChatColor.DARK_PURPLE + "▋▋▋▋";
        };
        return ChatColor.AQUA + "  Stacks" + ChatColor.WHITE + ": "
                + ChatColor.LIGHT_PURPLE + stacks
                + ChatColor.GRAY + " " + bar;
    }

    private static String crescendoLine(int evoLvl, long msLeft) {
        if (evoLvl < 3) {
            return ChatColor.DARK_PURPLE + "Crescendo" + ChatColor.WHITE + ": " + ChatColor.RED + "Locked";
        }
        if (msLeft <= 0) {
            return ChatColor.DARK_PURPLE + "Crescendo" + ChatColor.WHITE + ": " + ChatColor.GREEN + "Ready";
        }
        int sec = (int) Math.ceil(msLeft / 1000.0);
        return ChatColor.RED + "Crescendo" + ChatColor.WHITE + ": " + ChatColor.YELLOW + sec + "s";
    }

    /** Returns +percent value text, e.g., +35%. Input expects fractional (0.35). */
    private static String percent(double frac) {
        int pct = (int) Math.round(frac * 100.0);
        return (pct >= 0 ? "+" : "") + pct + "%";
    }

    /**
     * Winder/Tide-style curve: Evo 0..3 => factors 0..3 with different weights per key.
     * Just visual; real numbers come from EvoCore multipliers at runtime.
     */
    private static double abilityBonusPct(int evo, String key) {
        int factor = switch (evo) {
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 3;
            default -> 0;
        };

        String k = key == null ? "" : key.toLowerCase(java.util.Locale.ROOT);

        double bonus = switch (k) {
            // main burst (Echo)
            case "echo"   -> 0.35 * factor;
            // combo / echo window (Reverb)
            case "reverb" -> 0.25 * factor;
            default -> 0.0;
        };

        return bonus; // fractional
    }

    private static String trim(String s) {
        if (s == null) return "";
        if (s.length() <= 40) return s;
        return s.substring(0, 37) + "...";
    }
}
