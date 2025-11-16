package com.stellinova.syncblade;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

/**
 * SyncBlade sidebar HUD – mirrors Winder/Tide layout style, reskinned in purple.
 */
public class SyncScoreboardHud {

    private final SyncBladePlugin plugin;

    public SyncScoreboardHud(SyncBladePlugin plugin) {
        this.plugin = plugin;
        new Loop().runTaskTimer(plugin, 20L, 20L);
    }

    private class Loop extends BukkitRunnable {
        @Override
        public void run() {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!SyncAccessBridge.canUseSync(p)) {
                    ScoreboardManager sm = Bukkit.getScoreboardManager();
                    if (sm == null) continue;
                    // Give an empty board so we don't leak old SyncBlade lines
                    Scoreboard empty = sm.getNewScoreboard();
                    p.setScoreboard(empty);
                    continue;
                }
                refresh(p);
            }
        }
    }

    public void refresh(Player p) {
        ScoreboardManager sm = Bukkit.getScoreboardManager();
        if (sm == null) return;

        Scoreboard sb = sm.getNewScoreboard();
        Objective obj = sb.registerNewObjective(
                "synchud",
                "dummy",
                ChatColor.DARK_PURPLE + "SYNCBLADE"
        );
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        SyncPlayerData d = plugin.data(p);
        long now = System.currentTimeMillis();
        int evo = SyncEvoBridge.evo(p);

        int score = 15;

        // Top title / header
        add(obj, ChatColor.DARK_PURPLE + "» " + ChatColor.LIGHT_PURPLE + "Rune Status", score--);
        add(obj, ChatColor.GRAY + "----------------", score--);

        // Rune & Evo
        add(obj, ChatColor.GRAY + "Rune: " + ChatColor.LIGHT_PURPLE + "SyncBlade", score--);
        add(obj, ChatColor.GRAY + "Evo: " + ChatColor.AQUA + evo, score--);

        // Rhythm
        add(obj, ChatColor.GRAY + "Rhythm: " + ChatColor.WHITE + d.getRhythmStacks(), score--);

        add(obj, ChatColor.GRAY + " ", score--);

        // Ability section – styled like Winder/Tide (name + status)
        long echoCd = Math.max(0, d.getEchoReadyAt() - now);
        long revCd  = Math.max(0, d.getReverbReadyAt() - now);
        long creCd  = Math.max(0, d.getCrescendoReadyAt() - now);

        add(obj, ChatColor.DARK_PURPLE + "» " + ChatColor.LIGHT_PURPLE + "Abilities", score--);

        // Echo
        add(obj,
                ChatColor.GRAY + "Echo Step: " +
                (echoCd <= 0 ? ChatColor.GREEN + "READY"
                             : ChatColor.YELLOW + formatCd(echoCd)),
                score--);

        // Reverb
        add(obj,
                ChatColor.GRAY + "Reverb: " +
                (revCd <= 0 ? ChatColor.GREEN + "READY"
                            : ChatColor.YELLOW + formatCd(revCd)),
                score--);

        // Crescendo
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

        // Ult state indicator
        if (now < d.getCrescendoActiveUntil()) {
            add(obj, ChatColor.DARK_PURPLE + "Crescendo ACTIVE", score--);
        } else {
            add(obj, ChatColor.DARK_PURPLE + "Stay in rhythm", score--);
        }

        p.setScoreboard(sb);
    }

    private static String formatCd(long ms) {
        if (ms <= 0) return ChatColor.GREEN + "Ready";
        int sec = (int) Math.ceil(ms / 1000.0);
        // IMPORTANT: force string concat; ChatColor + int alone won't compile.
        return ChatColor.YELLOW + "" + sec + "s";
    }

    private static void add(Objective o, String text, int score) {
        o.getScore(text).setScore(score);
    }
}
