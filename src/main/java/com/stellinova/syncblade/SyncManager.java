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
import org.bukkit.util.Vector;

/**
 * SyncManager — single mega-manager like Winder:
 *  - Rhythm Flow (passive combo)
 *  - Echo Step (short dash + primed hit)
 *  - Reverb Strike (delayed echo hit)
 *  - Crescendo (Evo3 ultimate window)
 */
public class SyncManager implements Listener {

    private final SyncBladePlugin plugin;
    private BukkitRunnable tickTask;

    // Tuning
    private static final long RHYTHM_WINDOW_MS = 1600L;
    private static final int[] RHYTHM_CAP = {4, 6, 8, 10};

    private static final long ECHO_CD_BASE_MS      = 6_000L;
    private static final long REVERB_CD_BASE_MS    = 8_000L;
    private static final long CRESCENDO_CD_BASE_MS = 120_000L;
    private static final long CRESCENDO_DURATION_MS = 6_000L;

    private static final long REVERB_HIT_WINDOW_MS = 2_000L;

    public SyncManager(SyncBladePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        this.tickTask = new Tick().runTaskTimer(plugin, 1L, 1L);
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

    /* ---------------------- core combat events ---------------------- */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!(e.getEntity() instanceof LivingEntity target)) return;
        if (!SyncAccessBridge.canUseSync(p)) return;

        long now = System.currentTimeMillis();
        SyncPlayerData d = plugin.data(p);
        int evo = SyncEvoBridge.evo(p);

        // Rhythm Flow: increase stacks on chained hits
        if (now - d.getLastHitAt() <= RHYTHM_WINDOW_MS) {
            int cap = RHYTHM_CAP[Math.max(0, Math.min(3, evo))];
            d.setRhythmStacks(Math.min(cap, d.getRhythmStacks() + 1));
        } else {
            d.setRhythmStacks(1);
        }
        d.setLastHitAt(now);

        // base rhythm damage bonus
        double rhythmBonus = 0.06 * d.getRhythmStacks(); // +6% per stack
        rhythmBonus += 0.04 * evo; // evo adds base
        if (now < d.getCrescendoActiveUntil()) {
            rhythmBonus *= 1.4; // Crescendo amplifies rhythm payoff
        }
        double newDamage = e.getDamage() * (1.0 + Math.max(0.0, rhythmBonus));
        e.setDamage(newDamage);

        // Echo Step primed hit
        if (d.isEchoPrimed() && now <= d.getEchoPrimedUntil()) {
            d.setEchoPrimed(false);
            // extra punchy crit visual
            target.getWorld().spawnParticle(Particle.CRIT_MAGIC, target.getLocation().add(0, 1.0, 0),
                    26, 0.45, 0.6, 0.45, 0.04);
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.9f, 1.4f);
            e.setDamage(e.getDamage() * 1.20); // +20% on top
        }

        // Reverb Strike echo
        if (d.isReverbPrimed()) {
            if (now <= d.getReverbHitWindowUntil()) {
                d.setReverbPrimed(false);
                final double echoDamage = e.getDamage() * (0.40 + 0.10 * evo); // 40–70% echo
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!target.isValid() || target.isDead()) return;
                        target.damage(Math.max(0.0, echoDamage), p);
                        target.getWorld().spawnParticle(Particle.SONIC_BOOM, target.getLocation().add(0, 1.0, 0),
                                1, 0, 0, 0, 0);
                        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1.0f, 1.9f);
                    }
                }.runTaskLater(plugin, 10L);
            } else {
                d.setReverbPrimed(false);
            }
        }
    }

    /* ---------------------- ability triggers ---------------------- */

    /** Called from /sync echo (or future keybind) — shimmer dash + prime next hit. */
    public void triggerEchoStep(Player p) {
        if (!SyncAccessBridge.canUseSync(p)) return;
        long now = System.currentTimeMillis();
        SyncPlayerData d = plugin.data(p);
        if (now < d.getEchoReadyAt()) {
            sendAB(p, ChatColor.YELLOW + "Echo Step cooling down...");
            tinyPulse(p);
            return;
        }

        int evo = SyncEvoBridge.evo(p);
        double evoMult = SyncEvoBridge.m(p, "echo");
        double distance = 0.9 + 0.2 * evo;

        Vector dir = p.getLocation().getDirection().normalize();
        Vector vel = dir.multiply(distance * evoMult);
        vel.setY(Math.max(-0.4, Math.min(0.4, vel.getY())));
        p.setVelocity(vel);

        // mirage trail
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation(), 20, 0.6, 0.25, 0.6, 0.04);
        p.getWorld().spawnParticle(Particle.SPELL_INSTANT, p.getLocation(), 22, 0.7, 0.35, 0.7, 0.03);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.5f);

        d.setEchoPrimed(true);
        d.setEchoPrimedUntil(now + 2500L); // 2.5s to land enhanced hit

        long cd = scaledCd(ECHO_CD_BASE_MS, evo);
        d.setEchoReadyAt(now + cd);
        sendAB(p, ChatColor.LIGHT_PURPLE + "Echo primed!");
    }

    /** Called from /sync reverb — next hit gets an echo aftershock. */
    public void primeReverb(Player p) {
        if (!SyncAccessBridge.canUseSync(p)) return;
        long now = System.currentTimeMillis();
        SyncPlayerData d = plugin.data(p);
        if (now < d.getReverbReadyAt()) {
            sendAB(p, ChatColor.YELLOW + "Reverb cooling down...");
            tinyPulse(p);
            return;
        }

        int evo = SyncEvoBridge.evo(p);
        d.setReverbPrimed(true);
        d.setReverbHitWindowUntil(now + REVERB_HIT_WINDOW_MS + (evo * 250L)); // slightly longer at higher evo

        long cd = scaledCd(REVERB_CD_BASE_MS, evo);
        d.setReverbReadyAt(now + cd);

        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.9f, 1.8f);
        p.getWorld().spawnParticle(Particle.NOTE, p.getLocation().add(0, 1.8, 0),
                8, 0.4, 0.3, 0.4, 0.01);
        sendAB(p, ChatColor.AQUA + "Reverb primed");
    }

    /** Called from /sync crescendo — Evo3 only. */
    public void tryCrescendo(Player p) {
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
            tinyPulse(p);
            return;
        }

        d.setCrescendoActiveUntil(now + CRESCENDO_DURATION_MS);
        d.setCrescendoReadyAt(now + CRESCENDO_CD_BASE_MS);

        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.9f, 1.3f);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 2.0f);
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation(), 40, 1.2, 0.7, 1.2, 0.05);
        p.getWorld().spawnParticle(Particle.SPELL_MOB, p.getLocation(), 40, 1.2, 0.8, 1.2, 0.02);

        sendAB(p, ChatColor.DARK_PURPLE + "Crescendo!");
    }

    /* ---------------------- tick loop ---------------------- */

    private final class Tick extends BukkitRunnable {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!SyncAccessBridge.canUseSync(p)) continue;
                SyncPlayerData d = plugin.data(p);

                // Rhythm decay if you drop the beat
                if (d.getRhythmStacks() > 0 && now - d.getLastHitAt() > RHYTHM_WINDOW_MS * 2L) {
                    d.setRhythmStacks(0);
                }

                // Crescendo visuals while active
                if (now < d.getCrescendoActiveUntil()) {
                    if (now >= d.getCrescendoNextTickAt()) {
                        d.setCrescendoNextTickAt(now + 450L);
                        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1.0, 0),
                                10, 0.6, 0.4, 0.6, 0.03);
                        p.getWorld().spawnParticle(Particle.SPELL_MOB, p.getLocation().add(0, 0.4, 0),
                                10, 0.7, 0.5, 0.7, 0.02);
                    }
                }

                // Simple action bar rhythm HUD
                StringBuilder sb = new StringBuilder();
                sb.append(ChatColor.LIGHT_PURPLE).append("Rhythm ").append(ChatColor.WHITE).append(d.getRhythmStacks());
                if (d.isEchoPrimed()) sb.append(ChatColor.GRAY).append(" | ").append(ChatColor.AQUA).append("Echo");
                if (d.isReverbPrimed()) sb.append(ChatColor.GRAY).append(" | ").append(ChatColor.DARK_AQUA).append("Reverb");
                if (now < d.getCrescendoActiveUntil()) sb.append(ChatColor.GRAY).append(" | ").append(ChatColor.DARK_PURPLE).append("Crescendo");
                sendAB(p, sb.toString());
            }
        }
    }

    /* ---------------------- helpers ---------------------- */

    private long scaledCd(long base, int evo) {
        // Evo reduces cooldown modestly
        double factor = switch (evo) {
            case 1 -> 0.9;
            case 2 -> 0.8;
            case 3 -> 0.65;
            default -> 1.0;
        };
        return (long) Math.max(0L, Math.floor(base * factor));
    }

    private void tinyPulse(Player p) {
        p.getWorld().spawnParticle(Particle.SPELL_INSTANT, p.getLocation(), 6, 0.3, 0.2, 0.3, 0.01);
        p.getWorld().playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.8f);
    }

    private void sendAB(Player p, String msg) {
        try {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
        } catch (Throwable ignored) {}
    }
}
