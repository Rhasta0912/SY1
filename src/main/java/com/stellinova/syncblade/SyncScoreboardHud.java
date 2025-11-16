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
 * SyncBlade sidebar HUD – Winder/Tide-style, purple themed, no flicker.
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
                UUID id = p.getUniqueId();

                if (!SyncAccessBridge.canUseSync(p)) {
                    // If we had a HUD for this player, clear it once and forget
                    if (boards.containsKey(id)) {
                        Scoreboard empty = sm.getNewScoreboard();
                        p.setScoreboard(empty);
                        boards.remove(id);
                    }
                    continue;
                }

                refresh(p);
            }
        }
    }

    public void refresh(Player p) {
        ScoreboardManager sm = Bukkit.getScoreboardManager();
        if (sm == null) return;

        UUID id = p.getUniqueId();
        Scoreboard sb = boards.get(id);

        // Create once and attach to player – no more per-tick swapping
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
            // Ensure display slot/title in case something else touched it
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.setDisplayName(ChatColor.DARK_PURPLE + "SYNCBLADE");
        }

        // Clear old lines
        for (String entry : sb.getEntries()) {
            sb.resetScores(entry);
        }

        SyncPlayerData d = plugin.data(p);
        long now = System.currentTimeMillis();
        int evo = SyncEvoBridge.evo(p);

        int score = 15;

        // Header
        add(obj, ChatColor.DARK_PURPLE + "» " + ChatColor.LIGHT_PURPLE + "Rune Status", score--);
        add(obj, ChatColor.GRAY + "----------------", score--);

        // Rune / Evo / Rhythm
        add(obj, ChatColor.GRAY + "Rune: " + ChatColor.LIGHT_PURPLE + "SyncBlade", score--);
        add(obj, ChatColor.GRAY + "Evo: " + ChatColor.AQUA + evo, score--);
        add(obj, ChatColor.GRAY + "Rhythm: " + ChatColor.WHITE + d.getRhythmStacks(), score--);

        add(obj, ChatColor.GRAY + " ", score--);

        // Abilities section
        long echoCd = Math.max(0, d.getEchoReadyAt() - now);
        long revCd  = Math.max(0, d.getReverbReadyAt() - now);
        long creCd  = Math.max(0, d.getCrescendoReadyAt() - now);

        add(obj, ChatColor.DARK_PURPLE + "» " + ChatColor.LIGHT_PURPLE + "Abilities", score--);

        add(obj,
                ChatColor.GRAY + "Echo Step: " +
                        (echoCd <= 0 ? ChatColor.GREEN + "READY"
                                     : ChatColor.YELLOW + formatCd(echoCd)),
                score--);

        add(obj,
                ChatColor.GRAY + "Reverb: " +
                        (revCd <= 0 ? ChatColor.GREEN + "READY"
                                    : ChatColor.YELLOW + formatCd(revCd)),
                score--);

        String cresLine;
        if (evo < 3) {
            cresLine = ChatColor.RED + "LOCKED (Evo 3)";
        } else if (creCd <= 0) {
            cresLine = ChatColor.GREEN + "READY";
        } else {
            cresLine = ChatColor.YELLOW + formatCd(creCd);
        }
        add(obj, ChatColor.GRAY + "Crescendo: " + cresLine, score--);

        add(obj, ChatColor.GRAY + " ", score--);

        // Ult state
        if (now < d.getCrescendoActiveUntil()) {
            add(obj, ChatColor.DARK_PURPLE + "Crescendo ACTIVE", score--);
        } else {
            add(obj, ChatColor.DARK_PURPLE + "Stay in rhythm", score--);
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
