package com.stellinova.syncblade;

import com.example.evo.api.IEvoService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

/**
 * SyncBlade sidebar HUD – same infrastructure as Winder/Tide, reskinned purple.
 * Uses a fresh scoreboard like your other runes and hides when the rune is not active.
 */
public class SyncScoreboardHud {

    private final SyncBladePlugin plugin;
    @SuppressWarnings("unused")
    private final SyncManager manager; // kept for ctor compatibility / future use
    @SuppressWarnings("unused")
    private final IEvoService evo;     // reserved for direct EvoCore use

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
        }.runTaskTimer(plugin, 1L, 10L); // ~0.5s like Winder/Tide
    }

    public void refresh(Player p) {
        // Hide HUD if they don’t currently have the rune
        if (!SyncAccessBridge.canUseSync(p)) {
            ScoreboardManager sm = Bukkit.getScoreboardManager();
            if (sm == null) return;
            Scoreboard empty = sm.getNewScoreboard();
            p.setScoreboard(empty);
            return;
        }

        ScoreboardManager sm = Bukkit.getScoreboardManager();
        if (sm == null) return;
        Scoreboard sb = sm.getNewScoreboard();

        Objective obj = sb.getObjective("synchud");
        if (obj == null) {
            obj = sb.registerNewObjective("synchud", "dummy",
                    ChatColor.DARK_PURPLE + "SyncBlade");
        }
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.setDisplayName(ChatColor.DARK_PURPLE + "SyncBlade");

        // State – mirror Winder/Tide style
        SyncPlayerData d = plugin.data(p);
        long now = System.currentTimeMillis();
        int evoLvl = Math.max(0, Math.min(3, SyncEvoBridge.evo(p)));

        long echoLeft = Math.max(0L, d.getEchoReadyAt() - now);
        long revLeft  = Math.max(0L, d.getReverbReadyAt() - now);
        long creLeft  = Math.max(0L, d.getCrescendoReadyAt() - now);

        int s = 12;

        // EVO line
        add(obj, ChatColor.LIGHT_PURPLE + "EVO: " + ChatColor.AQUA + evoLvl, s--);

        // Echo Step (main mobility / precision ability)
        add(obj, abilityHeader("Echo Step", echoLeft), s--);
        add(obj, statLine("Flow", percent(abilityBonusPct(evoLvl, "echo"))), s--);

        // Reverb Strike
        add(obj, abilityHeader("Reverb Strike", revLeft), s--);
        add(obj, statLine("Echo", percent(abilityBonusPct(evoLvl, "reverb"))), s--);

        // Rhythm Flow (passive)
        add(obj, ChatColor.DARK_PURPLE + "Rhythm Flow", s--);
        add(obj, ChatColor.LIGHT_PURPLE + "  Stacks" + ChatColor.WHITE + ": +" + d.getRhythmStacks(), s--);

        // spacer
        add(obj, ChatColor.GRAY + "", s--);

        // Crescendo – Evo 3 ultimate style, like Breathtaking/Typhoon
        add(obj, crescendoLine(evoLvl, creLeft), s--);

        p.setScoreboard(sb);
    }

    // ---------- helpers (copied style from Winder/Tide) ----------

    private static void add(Objective obj, String text, int score) {
        String line = trim(text);
        // Ensure unique entry keys
        while (obj.getScoreboard().getEntries().contains(line)) {
            line += ChatColor.RESET;
        }
        obj.getScore(line).setScore(score);
    }

    /** Ability name purple when ready, red when cooling; shows yellow seconds while on cooldown. */
    private static String abilityHeader(String name, long msLeft) {
        boolean cooling = msLeft > 0;
        if (!cooling) return ChatColor.DARK_PURPLE + name;
        int sec = (int) Math.ceil(msLeft / 1000.0);
        return ChatColor.RED + name + ChatColor.GRAY + " (" + ChatColor.YELLOW + sec + "s" + ChatColor.GRAY + ")";
    }

    private static String statLine(String label, String value) {
        return ChatColor.LIGHT_PURPLE + "  " + label + ChatColor.WHITE + ": " + value;
    }

    /** EVO<3: Locked (red). EVO3: name “Crescendo” with Ready/seconds. */
    private static String crescendoLine(int evoLvl, long msLeft) {
        if (evoLvl < 3) {
            return ChatColor.DARK_PURPLE + "Crescendo" + ChatColor.WHITE + ": " + ChatColor.RED + "Locked";
        }
        boolean cooling = msLeft > 0;
        if (!cooling) {
            return ChatColor.DARK_PURPLE + "Crescendo" + ChatColor.WHITE + ": " + ChatColor.GREEN + "Ready";
        }
        int sec = (int) Math.ceil(msLeft / 1000.0);
        return ChatColor.RED + "Crescendo" + ChatColor.WHITE + ": " + ChatColor.YELLOW + sec + "s";
    }

    /** Returns +percent value as text, e.g., +35%. Input expects fractional (0.35). */
    private static String percent(double frac) {
        int pct = (int) Math.round(frac * 100.0);
        return (pct >= 0 ? "+" : "") + pct + "%";
    }

    /**
     * Matches Winder/Tide HUD behaviour: Evo 0..3 => factors 0..3 with bonuses per key.
     * Values are purely visual – do NOT affect gameplay.
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
            // Main rhythm ability gets the “big” stat curve (like Maelstrom / jump)
            case "echo"    -> 0.35 * factor;
            // Supporting abilities-normal bonuses
            case "reverb"  -> 0.25 * factor;
            default        -> 0.0;
        };

        return bonus; // fractional (e.g., 0.35, 0.75, 1.05)
    }

    private static String trim(String s) {
        if (s == null) return "";
        if (s.length() <= 40) return s;
        return s.substring(0, 37) + "...";
    }
}
