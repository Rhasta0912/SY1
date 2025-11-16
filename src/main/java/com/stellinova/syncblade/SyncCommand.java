package com.stellinova.syncblade;

import com.example.evo.api.IEvoService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /syncblade command:
 *  - /syncblade status
 *  - /syncblade rune
 *  - /syncblade reset
 *  - /syncblade echo
 *  - /syncblade reverb
 *  - /syncblade crescendo
 *  - /syncblade reload
 */
public class SyncCommand implements CommandExecutor {

    private final SyncBladePlugin plugin;
    private final SyncManager manager;
    private final SyncScoreboardHud hud;
    private final IEvoService evo; // nullable

    public SyncCommand(SyncBladePlugin plugin, SyncManager manager, SyncScoreboardHud hud, IEvoService evo) {
        this.plugin = plugin;
        this.manager = manager;
        this.hud = hud;
        this.evo = evo;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (args.length == 0) {
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "/syncblade status");
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "/syncblade rune" + ChatColor.GRAY + " — enable SyncBlade");
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "/syncblade reset" + ChatColor.GRAY + " — disable SyncBlade");
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "/syncblade echo" + ChatColor.GRAY + " — Echo Step");
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "/syncblade reverb" + ChatColor.GRAY + " — Reverb Strike");
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "/syncblade crescendo" + ChatColor.GRAY + " — Evo3 ultimate");
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "/syncblade reload" + ChatColor.GRAY + " — reload HUD");
            return true;
        }

        // status
        if (args[0].equalsIgnoreCase("status")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Players only.");
                return true;
            }
            SyncPlayerData d = plugin.data(p);
            int lvl = (evo != null ? evo.getEvoLevel(p.getUniqueId()) : SyncEvoBridge.evo(p));
            boolean can = SyncAccessBridge.canUseSync(p);

            sender.sendMessage(ChatColor.LIGHT_PURPLE + "SyncBlade Status");
            sender.sendMessage(ChatColor.GRAY + "  Rune: " + (can ? ChatColor.GREEN + "SyncBlade" : ChatColor.RED + "None"));
            sender.sendMessage(ChatColor.GRAY + "  Evo: " + ChatColor.LIGHT_PURPLE + lvl
                    + ChatColor.GRAY + " (EvoCore " + (evo != null ? "ON" : "OFF") + ")");
            sender.sendMessage(ChatColor.GRAY + "  Rhythm stacks: " + ChatColor.AQUA + d.getRhythmStacks());

            long now = System.currentTimeMillis();
            long cdEcho = Math.max(0, d.getEchoReadyAt() - now);
            long cdRev  = Math.max(0, d.getReverbReadyAt() - now);
            long cdCre  = Math.max(0, d.getCrescendoReadyAt() - now);

            sender.sendMessage(ChatColor.GRAY + "  Echo Step CD: " + (cdEcho == 0 ? "ready" : (cdEcho / 1000.0) + "s"));
            sender.sendMessage(ChatColor.GRAY + "  Reverb Strike CD: " + (cdRev == 0 ? "ready" : (cdRev / 1000.0) + "s"));
            sender.sendMessage(ChatColor.GRAY + "  Crescendo CD: " + (cdCre == 0 ? "ready" : (cdCre / 1000.0) + "s"));
            return true;
        }

        // rune
        if (args[0].equalsIgnoreCase("rune")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Players only.");
                return true;
            }
            SyncAccessBridge.grant(p);
            plugin.warmAccess(p);
            hud.refresh(p);
            sender.sendMessage(ChatColor.GREEN + "You tune yourself to the " + ChatColor.LIGHT_PURPLE + "SyncBlade" + ChatColor.GREEN + " rhythm.");
            return true;
        }

        // reset
        if (args[0].equalsIgnoreCase("reset")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Players only.");
                return true;
            }
            SyncAccessBridge.revoke(p);
            plugin.onRuneRevoked(p);
            hud.refresh(p);
            sender.sendMessage(ChatColor.YELLOW + "You fall out of rhythm. SyncBlade rune disabled.");
            return true;
        }

        // echo
        if (args[0].equalsIgnoreCase("echo")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Players only.");
                return true;
            }
            manager.triggerEchoStep(p);
            return true;
        }

        // reverb
        if (args[0].equalsIgnoreCase("reverb")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Players only.");
                return true;
            }
            manager.triggerReverb(p);
            return true;
        }

        // crescendo
        if (args[0].equalsIgnoreCase("crescendo")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Players only.");
                return true;
            }
            manager.triggerCrescendo(p);
            return true;
        }

        // reload HUD
        if (args[0].equalsIgnoreCase("reload")) {
            for (Player pl : Bukkit.getOnlinePlayers()) {
                try {
                    hud.refresh(pl);
                } catch (Throwable ignored) {}
            }
            sender.sendMessage(ChatColor.GREEN + "SyncBlade HUD reloaded.");
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use /syncblade for help.");
        return true;
    }
}
