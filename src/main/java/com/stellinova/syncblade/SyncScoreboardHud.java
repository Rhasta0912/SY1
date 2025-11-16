package com.stellinova.syncblade;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SyncBlade sidebar HUD – Winder/Tide style, purple themed, no flicker.
 * Always shows, but reflects whether the rune is actually active.
 */
public class SyncScoreboardHud {

    private final SyncBladePlugin plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public SyncScoreboardHud(SyncBladePlugin plugin) {
        this.plugin = plugin;
        new Loop().runTaskTimer(plugin, 20L, 20L);
    }

    private class Loop extends BukkitRunnable {
        @Override
        public void run() {
            ScoreboardManager sm = Bukkit.getScoreboardManager();
            if (sm == null) return;

            for (Player p : Bukkit.getOnlinePlayers()) {
                refresh(p);
            }
        }
    }

    public void refresh(Player p) {
        ScoreboardManager sm = Bukkit.getScoreboardManager();
        if (sm == null) return;

        UUID id = p.getUniqueId();
        Scoreboard sb = boards.get(id);

        // Create once and attach to player – no per-tick swapping.
        if (sb == null) {
            sb = sm.getNewScoreboard();
            boards.put(id, sb);
            p.setScoreboard(sb);
        }

        Objective obj = sb.getObjective("synchud");
        if (obj == null) {
            obj = sb.registerNewObjective("synchud", "dummy", ChatColor.DARK_PURPLE + "SYNCBLADE");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.setDisplayName(ChatColor.DARK_PURPLE + "SYNCBLADE");
        }

        // Clear previous lines
        for (String entry : sb.getEntries()) {
            sb.resetScores(entry);
        }

        boolean hasRune = SyncAccessBridge.canUseSync(p);
        SyncPlayerData d = plugin.data(p);
        long now = System.currentTimeMillis();
        int evo = SyncEvoBridge.evo(p);

        int score = 15;

        // Header
        add(obj, ChatColor.DARK_PURPLE + "» " + ChatColor.LIGHT_PURPLE + "Rune Status", score--);
        add(obj, ChatColor.GRAY + "----------------", score--);

        // Rune / Evo
        if (hasRune) {
            add(obj, ChatColor.GRAY + "Rune: " + ChatColor.LIGHT_PURPLE + "SyncBlade", score--);
        } else {
            add(obj, ChatColor.GRAY + "Rune: " + ChatColor.RED + "None", score--);
        }
        add(obj, ChatColor.GRAY + "Evo: " + ChatColor.AQUA + evo, score--);

        // Rhythm (only meaningful if rune)
        int rhythm = hasRune ? d.getRhythmStacks() : 0;
        add(obj, ChatColor.GRAY + "Rhythm: " + ChatColor.WHITE + rhythm, score--);

        add(obj, ChatColor.GRAY + " ", score--);

        // Abilities section
        long echoCd = hasRune ? Math.max(0, d.getEchoReadyAt() - now) : 0;
        long revCd  = hasRune ? Math.max(0, d.getReverbReadyAt() - now) : 0;
        long creCd  = hasRune ? Math.max(0, d.getCrescendoReadyAt() - now) : 0;

        add(obj, ChatColor.DARK_PURPLE + "» " + ChatColor.LIGHT_PURPLE + "Abilities", score--);

        // Echo
        String echoLine;
        if (!hasRune) {
            echoLine = ChatColor.RED + "NO RUNE";
        } else if (echoCd <= 0) {
            echoLine = ChatColor.GREEN + "READY";
        } else {
            echoLine = ChatColor.YELLOW + formatCd(echoCd);
        }
        add(obj, ChatColor.GRAY + "Echo Step: " + echoLine, score--);

        // Reverb
        String revLine;
        if (!hasRune) {
            revLine = ChatColor.RED + "NO RUNE";
        } else if (revCd <= 0) {
            revLine = ChatColor.GREEN + "READY";
        } else {
            revLine = ChatColor.YELLOW + formatCd(revCd);
        }
        add(obj, ChatColor.GRAY + "Reverb: " + revLine, score--);

        // Crescendo
        String cresLine;
        if (!hasRune) {
            cresLine = ChatColor.RED + "NO RUNE";
        } else if (evo < 3) {
            cresLine = ChatColor.RED + "LOCKED (Evo 3)";
        } else if (creCd <= 0) {
            cresLine = ChatColor.GREEN + "READY";
        } else {
            cresLine = ChatColor.YELLOW + formatCd(creCd);
        }
        add(obj, ChatColor.GRAY + "Crescendo: " + cresLine, score--);

        add(obj, ChatColor.GRAY + " ", score--);

        // Ult state flavor
        if (hasRune && now < d.getCrescendoActiveUntil()) {
            add(obj, ChatColor.DARK_PURPLE + "Crescendo ACTIVE", score--);
        } else if (hasRune) {
            add(obj, ChatColor.DARK_PURPLE + "Stay in rhythm", score--);
        } else {
            add(obj, ChatColor.DARK_GRAY + "Select a rune", score--);
        }
    }

    private static String formatCd(long ms) {
        if (ms <= 0) return ChatColor.GREEN + "Ready";
        int sec = (int) Math.ceil(ms / 1000.0);
        // Force string concat so ChatColor + int compiles
        return ChatColor.YELLOW + "" + sec + "s";
    }

    private static void add(Objective o, String text, int score) {
        o.getScore(text).setScore(score);
    }
}
