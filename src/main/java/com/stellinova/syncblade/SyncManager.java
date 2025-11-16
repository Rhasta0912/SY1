package com.stellinova.syncblade;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * SyncManager – full ability engine for SyncBlade.
 * Contains:
 *  - Rhythm Flow
 *  - Echo Step (dash)
 *  - Reverb Strike (delayed hit)
 *  - Crescendo (Evo 3 ultimate)
 *  - Purple glowing eye FX
 */
public class SyncManager implements Listener {

    private final SyncBladePlugin plugin;
    private BukkitTask tickTask;

    public SyncManager(SyncBladePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        tickTask = new Tick().runTaskTimer(plugin, 1L, 1L);
    }

    public void shutdown() {
        try { if (tickTask != null) tickTask.cancel(); }
        catch (Throwable ignored) {}
    }

    // ----------------------------------------------------------------------
    // Combat Event – Rhythm, Echo primed hit, Reverb echo-hit
    // ----------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!(e.getEntity() instanceof LivingEntity target)) return;
        if (!SyncAccessBridge.canUseSync(p)) return;

        SyncPlayerData d = plugin.data(p);
        long now = System.currentTimeMillis();

        int evo = SyncEvoBridge.evo(p);

        // Rhythm flow
        if (now - d.getLastHitAt() <= 1600L) {
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
        double rhythmBonus = 0.06 * d.getRhythmStacks();  // +6% each
        rhythmBonus += 0.04 * evo;                        // + per evo
        if (now < d.getCrescendoActiveUntil()) rhythmBonus *= 1.4;

        e.setDamage(e.getDamage() * (1.0 + rhythmBonus));

        // Echo Step primed hit
        if (d.isEchoPrimed() && now <= d.getEchoPrimedUntil()) {
            d.setEchoPrimed(false);

            // FX
            target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0),
                    20, 0.4, 0.4, 0.4, 0.01);
            target.getWorld().playSound(target.getLocation(),
                    Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.4f);

            e.setDamage(e.getDamage() * 1.20); // +20%
        }

        // Reverb Strike delayed echo
        if (d.isReverbPrimed()) {
            if (now <= d.getReverbHitWindowUntil()) {
                d.setReverbPrimed(false);

                double echoDamage = e.getDamage() * (0.40 + 0.10 * evo);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!target.isValid()) return;
                        target.damage(echoDamage, p);

                        target.getWorld().spawnParticle(Particle.SONIC_BOOM,
                                target.getLocation().add(0, 1, 0), 1);
                        target.getWorld().playSound(target.getLocation(),
                                Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1.9f);
                    }
                }.runTaskLater(plugin, 10L);

            } else {
                d.setReverbPrimed(false);
            }
        }
    }

    // ----------------------------------------------------------------------
    // Ability: Echo Step (dash forward)
    // Called from /syncblade echo
    // ----------------------------------------------------------------------
    public void triggerEchoStep(Player p) {
        if (!SyncAccessBridge.canUseSync(p)) return;
        long now = System.currentTimeMillis();
        SyncPlayerData d = plugin.data(p);
        int evo = SyncEvoBridge.evo(p);

        if (now < d.getEchoReadyAt()) {
            sendAB(p, ChatColor.YELLOW + "Echo cooldown...");
            pulse(p);
            return;
        }

        double mult = SyncEvoBridge.m(p, "echo");
        double distance = (0.9 + 0.2 * evo) * mult;

        Vector dash = p.getLocation().getDirection().normalize().multiply(distance);
        dash.setY(Math.max(-0.4, Math.min(0.4, dash.getY())));
        p.setVelocity(dash);

        // FX
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation(),
                20, 0.6, 0.25, 0.6, 0.05);
        p.getWorld().spawnParticle(Particle.INSTANT_EFFECT, p.getLocation(),
                20, 0.6, 0.25, 0.6, 0.05);
        p.getWorld().playSound(p.getLocation(),
                Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.5f);

        d.setEchoPrimed(true);
        d.setEchoPrimedUntil(now + 2500L);

        long cd = switch (evo) {
            case 1 -> 5400L;
            case 2 -> 4800L;
            case 3 -> 3900L;
            default -> 6000L;
        };
        d.setEchoReadyAt(now + cd);

        sendAB(p, ChatColor.AQUA + "Echo primed!");
    }

    // ----------------------------------------------------------------------
    // Ability: Reverb Strike
    // ----------------------------------------------------------------------
    public void triggerReverb(Player p) {
        if (!SyncAccessBridge.canUseSync(p)) return;
        long now = System.currentTimeMillis();
        SyncPlayerData d = plugin.data(p);
        int evo = SyncEvoBridge.evo(p);

        if (now < d.getReverbReadyAt()) {
            sendAB(p, ChatColor.YELLOW + "Reverb cooldown...");
            pulse(p);
            return;
        }

        long duration = 1800L + (evo * 200L);
        d.setReverbPrimed(true);
        d.setReverbHitWindowUntil(now + duration);

        long cd = switch (evo) {
            case 1 -> 7200L;
            case 2 -> 6600L;
            case 3 -> 6000L;
            default -> 8000L;
        };
        d.setReverbReadyAt(now + cd);

        p.getWorld().playSound(p.getLocation(),
                Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.8f);
        p.spawnParticle(Particle.NOTE, p.getLocation().add(0, 1.8, 0),
                10, 0.3, 0.3, 0.3, 0.01);

        sendAB(p, ChatColor.LIGHT_PURPLE + "Reverb primed");
    }

    // ----------------------------------------------------------------------
    // Ability: Crescendo (Evo 3 ultimate)
    // ----------------------------------------------------------------------
    public void triggerCrescendo(Player p) {
        if (!SyncAccessBridge.canUseSync(p)) return;

        SyncPlayerData d = plugin.data(p);
        long now = System.currentTimeMillis();
        int evo = SyncEvoBridge.evo(p);

        if (evo < 3) {
            sendAB(p, ChatColor.RED + "Requires Evo 3");
            return;
        }
        if (now < d.getCrescendoReadyAt()) {
            sendAB(p, ChatColor.YELLOW + "Crescendo cooldown...");
            pulse(p);
            return;
        }

        d.setCrescendoActiveUntil(now + 6000L);
        d.setCrescendoReadyAt(now + 120_000L);

        p.getWorld().playSound(p.getLocation(),
                Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.3f);
        p.getWorld().spawnParticle(Particle.END_ROD,
                p.getLocation(), 40, 1.2, 0.7, 1.2, 0.05);

        sendAB(p, ChatColor.DARK_PURPLE + "Crescendo!");
    }

    // ----------------------------------------------------------------------
    // Tick Loop – Rhythm decay, Crescendo ticks, Purple Eyes
    // ----------------------------------------------------------------------
    private class Tick extends BukkitRunnable {
        @Override
        public void run() {
            long now = System.currentTimeMillis();

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!SyncAccessBridge.canUseSync(p)) continue;

                SyncPlayerData d = plugin.data(p);
                int evo = SyncEvoBridge.evo(p);

                // Rhythm decay
                if (d.getRhythmStacks() > 0 &&
                        now - d.getLastHitAt() > (1600L * 2L)) {
                    d.setRhythmStacks(0);
                }

                // Crescendo visuals
                if (now < d.getCrescendoActiveUntil()) {
                    p.spawnParticle(Particle.END_ROD,
                            p.getLocation().add(0, 1, 0),
                            8, 0.4, 0.4, 0.4, 0.03);
                }

                // Purple glowing eyes
                tickEyes(p, evo);
            }
        }
    }

    // ----------------------------------------------------------------------
    // Purple eye glow
    // ----------------------------------------------------------------------
    private void tickEyes(Player p, int evo) {
        if (evo <= 0) return;

        Location eye = p.getEyeLocation();
        Location left  = eye.clone()
                .add(eye.getDirection().rotateAroundY(Math.PI / 2).normalize().multiply(0.09))
                .add(0, -0.07, 0);
        Location right = eye.clone()
                .add(eye.getDirection().rotateAroundY(-Math.PI / 2).normalize().multiply(0.09))
                .add(0, -0.07, 0);

        eye.getWorld().spawnParticle(Particle.END_ROD, left, 0);
        eye.getWorld().spawnParticle(Particle.END_ROD, right, 0);

        p.addPotionEffect(new PotionEffect(
                PotionEffectType.GLOWING,
                15, 0, false, false, true
        ));
    }

    // ----------------------------------------------------------------------
    // Utility
    // ----------------------------------------------------------------------
    private void sendAB(Player p, String msg) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
    }

    private void pulse(Player p) {
        p.getWorld().spawnParticle(Particle.INSTANT_EFFECT,
                p.getLocation(), 8, 0.3, 0.2, 0.3, 0.01);
        p.getWorld().playSound(p.getLocation(),
                Sound.UI_BUTTON_CLICK, 1f, 1.8f);
    }

    public void onRuneRevoked(Player p) {
        SyncPlayerData d = plugin.data(p);
        d.setRhythmStacks(0);
        d.setEchoPrimed(false);
        d.setReverbPrimed(false);
        d.setCrescendoActiveUntil(0);
    }
}
