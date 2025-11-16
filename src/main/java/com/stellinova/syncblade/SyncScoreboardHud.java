package com.stellinova.syncblade;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

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
                    p.setScoreboard(sm.getNewScoreboard());
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
                ChatColor.DARK_PURPLE + "SyncBlade"
        );
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        SyncPlayerData d = plugin.data(p);
        long now = System.currentTimeMillis();
        int evo = SyncEvoBridge.evo(p);

        long echoCd = Math.max(0, d.getEchoReadyAt() - now);
        long revCd  = Math.max(0, d.getReverbReadyAt() - now);
        long creCd  = Math.max(0, d.getCrescendoReadyAt() - now);

        int s = 12;

        add(obj, ChatColor.LIGHT_PURPLE + "EVO: " + ChatColor.AQUA + evo, s--);

        add(obj, ChatColor.AQUA + "Rhythm: " + ChatColor.WHITE + d.getRhythmStacks(), s--);

        add(obj, ChatColor.GRAY + "Echo Step: " + formatCd(echoCd), s--);
        add(obj, ChatColor.GRAY + "Reverb: " + formatCd(revCd), s--);

        if (evo < 3) {
            add(obj, ChatColor.GRAY + "Crescendo: " + ChatColor.RED + "Locked", s--);
        } else {
            add(obj, ChatColor.GRAY + "Crescendo: " + formatCd(creCd), s--);
        }

        p.setScoreboard(sb);
    }

    private static String formatCd(long ms) {
        if (ms <= 0) return ChatColor.GREEN + "Ready";
        int sec = (int) Math.ceil(ms / 1000.0);
        return ChatColor.YELLOW + "" + sec + "s";
    }

    private static void add(Objective o, String text, int score) {
        o.getScore(text).setScore(score);
    }
}
