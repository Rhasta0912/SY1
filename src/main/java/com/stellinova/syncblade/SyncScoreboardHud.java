package com.stellinova.syncblade;

import com.example.evo.api.IEvoService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

public class SyncScoreboardHud {

    private final SyncBladePlugin plugin;
    private final SyncManager manager;
    private final IEvoService evo;

    private BukkitTask task;

    public SyncScoreboardHud(SyncBladePlugin plugin, SyncManager manager, IEvoService evo) {
        this.plugin = plugin;
        this.manager = manager;
        this.evo = evo;
        start();
    }

    public void shutdown() {
        try { if (task != null) task.cancel(); }
        catch (Throwable ignored) {}
    }

    private void start() {
        task = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    refresh(p);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void refresh(Player p) {
        if (!SyncAccessBridge.canUseSync(p)) {
            clear(p);
            return;
        }

        ScoreboardManager sm = Bukkit.getScoreboardManager();
        if (sm == null) return;

        Scoreboard sb = sm.getNewScoreboard();
        Objective o = sb.registerNewObjective(
                "synchud", "dummy",
                ChatColor.DARK_PURPLE + "SyncBlade"
        );
        o.setDisplaySlot(DisplaySlot.SIDEBAR);

        SyncPlayerData d = plugin.data(p);
        long now = System.currentTimeMillis();
        int evoLvl = SyncEvoBridge.evo(p);

        long echo = Math.max(0, d.getEchoReadyAt() - now);
        long rev  = Math.max(0, d.getReverbReadyAt() - now);
        long cre  = Math.max(0, d.getCrescendoReadyAt() - now);

        int s = 12;

        line(o, ChatColor.LIGHT_PURPLE + "EVO: " + ChatColor.AQUA + evoLvl, s--);

        // Echo
        if (echo <= 0) line(o, ChatColor.AQUA + "Echo Step: " + ChatColor.GREEN + "Ready", s--);
        else line(o, ChatColor.AQUA + "Echo Step: " + ChatColor.YELLOW + (echo/1000) + "s", s--);

        // Reverb
        if (rev <= 0) line(o, ChatColor.AQUA + "Reverb: " + ChatColor.GREEN + "Ready", s--);
        else line(o, ChatColor.AQUA + "Reverb: " + ChatColor.YELLOW + (rev/1000) + "s", s--);

        // Rhythm
        line(o, ChatColor.DARK_PURPLE + "Rhythm: " +
                ChatColor.WHITE + d.getRhythmStacks(), s--);

        // Crescendo
        if (evoLvl < 3) {
            line(o, ChatColor.DARK_PURPLE + "Crescendo: " + ChatColor.RED + "Locked", s--);
        } else if (cre <= 0) {
            line(o, ChatColor.DARK_PURPLE + "Crescendo: " + ChatColor.GREEN + "Ready", s--);
        } else {
            line(o, ChatColor.DARK_PURPLE + "Crescendo: " + ChatColor.YELLOW + (cre/1000) + "s", s--);
        }

        p.setScoreboard(sb);
    }

    private void clear(Player p) {
        ScoreboardManager sm = Bukkit.getScoreboardManager();
        if (sm == null) return;
        p.setScoreboard(sm.getNewScoreboard());
    }

    private void line(Objective o, String text, int score) {
        o.getScore(text).setScore(score);
    }
}
