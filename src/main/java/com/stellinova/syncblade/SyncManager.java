package com.stellinova.syncblade;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * SyncManager — ability logic for SyncBlade:
 *  - Rhythm Flow passive
 *  - Echo Step
 *  - Reverb Strike
 *  - Crescendo (Evo 3 ult)
 */
public class SyncManager implements Listener {

    private final SyncBladePlugin plugin;
    private BukkitTask tickTask;

    // Tuning
    private static final long RHYTHM_WINDOW_MS = 1600L;

    private static final long ECHO_CD_BASE_MS       = 6_000L;
    private static final long REVERB_CD_BASE_MS     = 8_000L;
    private static final long CRESCENDO_CD_BASE_MS  = 120_000L;
    private static final long CRESCENDO_DURATION_MS = 6_000L;

    public SyncManager(SyncBladePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        tickTask = new Tick().runTaskTimer(plugin, 1L, 1L);
    }

    public void shutdown() {
        try {
            if (tickTask != null) {
                tickTask.cancel();
                tickTask = null;
            }
        } catch (Throwable ignored) {}
    }

    public void onRuneRevoked(Player p) {
        SyncPlayerData d = plugin.data(p);
        d.setRhythmStacks(0);
        d.setEchoPrimed(false);
        d.setReverbPrimed(false);
        d.setCrescendoActiveUntil(0L);
        sendAB(p, "");
    }

    // ----------------------------------------------------------------------
    // Combat event: Rhythm, Echo primed, Reverb echo-hit
    // ----------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!(e.getEntity() instanceof LivingEntity target)) return;
        if (!SyncAccessBridge.canUseSync(p)) return;

        SyncPlayerData d = plugin.data(p);
        long now = System.currentTimeMillis();
        int evo = SyncEvoBridge.evo(p);

        // Rhythm Flow
        if (now - d.getLastHitAt() <= RHYTHM_WINDOW_MS) {
            int cap = switch (evo) {
                case 1 -> 6;
                case 2 -> 8;
                case 3 -> 10;
                default -> 4;
            };
            d.setRhythmStacks(Math.min(cap, d.getRhythmStacks() + 1));
        } else {
            d.setRhythmStacks(1);
        }
        d.setLastHitAt(now);

        // Rhythm bonus damage
        double rhythmBonus = 0.06 * d.getRhythmStacks(); // +6% per stack
        rhythmBonus += 0.04 * evo;
        if (now < d.getCrescendoActiveUntil()) {
            rhythmBonus *= 1.4;
        }
        e.setDamage(e.getDamage() * (1.0 + Math.max(0, rhythmBonus)));

        // Echo Step primed hit
        if (d.isEchoPrimed() && now <= d.getEchoPrimedUntil()) {
            d.setEchoPrimed(false);

            target.getWorld().spawnParticle(
                    Particle.CRIT,
                    target.getLocation().add(0, 1.0, 0),
                    20, 0.4, 0.4, 0.4, 0.01
            );
            target.getWorld().playSound(
                    target.getLocation(),
                    Sound.ENTITY_PLAYER_ATTACK_CRIT,
                    1.0f, 1.4f
            );

            e.setDamage(e.getDamage() * 1.20); // extra 20%
        }

        // Reverb Strike echo
        if (d.isReverbPrimed()) {
            if (now <= d.getReverbHitWindowUntil()) {
                d.setReverbPrimed(false);

                double echoDamage = e.getDamage() * (0.40 + 0.10 * evo);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!target.isValid() || target.isDead()) return;
                        target.damage(Math.max(0.0, echoDamage), p);
                        target.getWorld().spawnParticle(
                                Particle.SONIC_BOOM,
                                target.getLocation().add(0, 1.0, 0),
                                1, 0, 0, 0, 0
                        );
                        target.getWorld().playSound(
                                target.getLocation(),
                                Sound.BLOCK_NOTE_BLOCK_BIT,
                                1.0f, 1.9f
                        );
                    }
                }.runTaskLater(plugin, 10L);
            } else {
                d.setReverbPrimed(false);
            }
        }
    }

    // ----------------------------------------------------------------------
    // /syncblade echo
    // ----------------------------------------------------------------------
    public void triggerEchoStep(Player p) {
        if (!SyncAccessBridge.canUseSync(p)) return;
        long now = System.currentTimeMillis();
        SyncPlayerData d = plugin.data(p);
        int evo = SyncEvoBridge.evo(p);

        if (now < d.getEchoReadyAt()) {
            sendAB(p, ChatColor.YELLOW + "Echo Step cooling down...");
            pulse(p);
            return;
        }

        double mult = SyncEvoBridge.m(p, "echo");
        double distance = (0.9 + 0.2 * evo) * mult;

        Vector dir = p.getLocation().getDirection().normalize();
        Vector vel = dir.multiply(distance);
        vel.setY(Math.max(-0.4, Math.min(0.4, vel.getY())));
        p.setVelocity(vel);

        p.getWorld().spawnParticle(
                Particle.END_ROD,
                p.getLocation(),
                20, 0.6, 0.25, 0.6, 0.04
        );
        p.getWorld().spawnParticle(
                Particle.INSTANT_EFFECT,
                p.getLocation(),
                22, 0.7, 0.35, 0.7, 0.03
        );
        p.getWorld().playSound(
                p.getLocation(),
                Sound.ENTITY_ENDERMAN_TELEPORT,
                0.8f, 1.5f
        );

        d.setEchoPrimed(true);
        d.setEchoPrimedUntil(now + 2500L);

        long cd = scaledCd(ECHO_CD_BASE_MS, evo);
        d.setEchoReadyAt(now + cd);

        sendAB(p, ChatColor.LIGHT_PURPLE + "Echo primed!");
    }

    // ----------------------------------------------------------------------
    // /syncblade reverb
    // ----------------------------------------------------------------------
    public void triggerReverb(Player p) {
        if (!SyncAccessBridge.canUseSync(p)) return;
        long now = System.currentTimeMillis();
        SyncPlayerData d = plugin.data(p);
        int evo = SyncEvoBridge.evo(p);

        if (now < d.getReverbReadyAt()) {
            sendAB(p, ChatColor.YELLOW + "Reverb cooling down...");
            pulse(p);
            return;
        }

        long window = 1800L + (evo * 200L);
        d.setReverbPrimed(true);
        d.setReverbHitWindowUntil(now + window);

        long cd = scaledCd(REVERB_CD_BASE_MS, evo);
        d.setReverbReadyAt(now + cd);

        p.getWorld().playSound(
                p.getLocation(),
                Sound.BLOCK_NOTE_BLOCK_CHIME,
                0.9f, 1.8f
        );
        p.getWorld().spawnParticle(
                Particle.NOTE,
                p.getLocation().add(0, 1.8, 0),
                8, 0.4, 0.3, 0.4, 0.01
        );
        sendAB(p, ChatColor.AQUA + "Reverb primed");
    }

    // ----------------------------------------------------------------------
    // /syncblade crescendo
    // ----------------------------------------------------------------------
    public void triggerCrescendo(Player p) {
        if (!SyncAccessBridge.canUseSync(p)) return;
        SyncPlayerData d = plugin.data(p);
        long now = System.currentTimeMillis();
        int evo = SyncEvoBridge.evo(p);

        if (evo < 3) {
            sendAB(p, ChatColor.RED + "Crescendo requires Evo 3.");
            return;
        }
        if (now < d.getCrescendoReadyAt()) {
            sendAB(p, ChatColor.YELLOW + "Crescendo cooling down...");
            pulse(p);
            return;
        }

        d.setCrescendoActiveUntil(now + CRESCENDO_DURATION_MS);
        d.setCrescendoReadyAt(now + CRESCENDO_CD_BASE_MS);

        p.getWorld().playSound(
                p.getLocation(),
                Sound.ENTITY_ENDER_DRAGON_GROWL,
                0.9f, 1.3f
        );
        p.getWorld().playSound(
                p.getLocation(),
                Sound.BLOCK_NOTE_BLOCK_BELL,
                1.0f, 2.0f
        );
        p.getWorld().spawnParticle(
                Particle.END_ROD,
                p.getLocation(),
                40, 1.2, 0.7, 1.2, 0.05
        );
        p.getWorld().spawnParticle(
                Particle.INSTANT_EFFECT,
                p.getLocation(),
                40, 1.2, 0.8, 1.2, 0.02
        );

        sendAB(p, ChatColor.DARK_PURPLE + "Crescendo!");
    }

    // ----------------------------------------------------------------------
    // Tick loop – decay + small actionbar HUD
    // ----------------------------------------------------------------------
    private final class Tick extends BukkitRunnable {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!SyncAccessBridge.canUseSync(p)) continue;

                SyncPlayerData d = plugin.data(p);

                // Rhythm decay
                if (d.getRhythmStacks() > 0 &&
                        now - d.getLastHitAt() > RHYTHM_WINDOW_MS * 2L) {
                    d.setRhythmStacks(0);
                }

                // Simple actionbar indicator
                StringBuilder sb = new StringBuilder();
                sb.append(ChatColor.LIGHT_PURPLE)
                  .append("Rhythm ")
                  .append(ChatColor.WHITE)
                  .append(d.getRhythmStacks());
                if (d.isEchoPrimed()) sb.append(ChatColor.GRAY).append(" | ").append(ChatColor.AQUA).append("Echo");
                if (d.isReverbPrimed()) sb.append(ChatColor.GRAY).append(" | ").append(ChatColor.DARK_AQUA).append("Reverb");
                if (now < d.getCrescendoActiveUntil()) sb.append(ChatColor.GRAY).append(" | ").append(ChatColor.DARK_PURPLE).append("Crescendo");

                sendAB(p, sb.toString());
            }
        }
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------
    private long scaledCd(long base, int evo) {
        double factor = switch (evo) {
            case 1 -> 0.9;
            case 2 -> 0.8;
            case 3 -> 0.65;
            default -> 1.0;
        };
        return (long) Math.max(0L, Math.floor(base * factor));
    }

    private void pulse(Player p) {
        p.getWorld().spawnParticle(
                Particle.INSTANT_EFFECT,
                p.getLocation(),
                6, 0.3, 0.2, 0.3, 0.01
        );
        p.getWorld().playSound(
                p.getLocation(),
                Sound.UI_BUTTON_CLICK,
                0.6f, 1.8f
        );
    }

    private void sendAB(Player p, String msg) {
        try {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
        } catch (Throwable ignored) {}
    }
}
