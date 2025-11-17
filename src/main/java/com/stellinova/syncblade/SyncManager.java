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
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SyncManager — ability logic for SyncBlade:
 *  - Rhythm Flow passive
 *  - Echo Step
 *  - Reverb Strike
 *  - Crescendo (Evo 3 ult)
 *
 * Triggers:
 *  - Single SHIFT tap        -> Echo Step
 *  - Double SHIFT tap        -> Reverb Strike
 *  - Double-tap F (swap-hand)-> Crescendo (Evo 3 + 5+ Rhythm)
 *
 * Commands still work:
 *  /syncblade echo, /syncblade reverb, /syncblade crescendo
 */
public class SyncManager implements Listener {

    private final SyncBladePlugin plugin;
    private BukkitTask tickTask;

    // timing trackers for inputs
    private final Map<UUID, Long> lastSneak = new HashMap<>();
    private final Map<UUID, Long> lastSwapF = new HashMap<>();

    // Tuning
    private static final long RHYTHM_WINDOW_MS = 1600L;

    // More cooldown than before (heavier like Winder)
    private static final long ECHO_CD_BASE_MS       = 9_000L;
    private static final long REVERB_CD_BASE_MS     = 12_000L;
    private static final long CRESCENDO_CD_BASE_MS  = 180_000L;
    private static final long CRESCENDO_DURATION_MS = 6_000L;

    // double-tap window (ms) for shift/F
    private static final long DOUBLE_TAP_WINDOW_MS = 250L;

    // Hunger costs (heavier like Winder)
    private static final int ECHO_HUNGER_COST      = 4;  // 2 drumsticks
    private static final int REVERB_HUNGER_COST    = 5;  // 2.5 drumsticks
    private static final int CRESCENDO_HUNGER_COST = 8;  // 4 drumsticks

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
    // Combat event: Rhythm, Echo primed, Reverb echo-hit, Ult Echo CDR
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

            // Evo-scaled slowness + horizontal freeze (no bhop escape)
            int stunTicks = switch (evo) {
                case 1 -> 80;  // 4s
                case 2 -> 100; // 5s
                case 3 -> 120; // 6s
                default -> 60; // 3s
            };
            target.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS, stunTicks, 4, false, false, false
            )); // Slowness V

            // Freeze horizontal movement for stun duration
            new BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    if (!target.isValid() || target.isDead() || ticks++ >= stunTicks) {
                        cancel();
                        return;
                    }
                    Vector v = target.getVelocity();
                    v.setX(0);
                    v.setZ(0);
                    target.setVelocity(v);
                }
            }.runTaskTimer(plugin, 0L, 1L);

            e.setDamage(e.getDamage() * 1.20); // extra 20%
        }

        // Reverb Strike echo
        if (d.isReverbPrimed()) {
            if (now <= d.getReverbHitWindowUntil()) {
                d.setReverbPrimed(false);

                // visual + % max HP damage: total 25% HP, Evo 3 = 3 pulses
                final int pulseCount = (evo >= 3) ? 3 : 1;
                final double damagePerPulse = (target.getMaxHealth() * 0.25) / pulseCount;

                for (int i = 0; i < pulseCount; i++) {
                    long delay = 10L + (i * 6L);
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (!target.isValid() || target.isDead()) return;

                            target.damage(damagePerPulse, p);

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
                    }.runTaskLater(plugin, delay);
                }
            } else {
                d.setReverbPrimed(false);
            }
        }

        // While Crescendo is active, each hit shaves Echo's cooldown heavily
        if (now < d.getCrescendoActiveUntil() && d.getEchoReadyAt() > now) {
            long shave = 600L; // 0.6s per hit
            long newReady = d.getEchoReadyAt() - shave;
            if (newReady < now) newReady = now;
            d.setEchoReadyAt(newReady);
        }
    }

    // ----------------------------------------------------------------------
    // SHIFT triggers:
    //  - single tap  -> Echo Step
    //  - double tap  -> Reverb Strike
    // ----------------------------------------------------------------------
    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (!SyncAccessBridge.canUseSync(p)) return;

        // Only care about starting to sneak (key pressed)
        if (!e.isSneaking()) return;

        long now = System.currentTimeMillis();
        UUID id = p.getUniqueId();
        long last = lastSneak.getOrDefault(id, 0L);
        lastSneak.put(id, now);

        // Double tap within window => Reverb Strike
        if (now - last <= DOUBLE_TAP_WINDOW_MS) {
            triggerReverb(p);
        } else {
            // Single tap => Echo Step
            triggerEchoStep(p);
        }
    }

    // ----------------------------------------------------------------------
    // F-key triggers:
    //  - double tap -> Crescendo (Evo 3 + 5+ Rhythm)
    // ----------------------------------------------------------------------
    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!SyncAccessBridge.canUseSync(p)) return;

        e.setCancelled(true); // prevent swapping when rune is active

        long now = System.currentTimeMillis();
        UUID id = p.getUniqueId();
        long last = lastSwapF.getOrDefault(id, 0L);
        lastSwapF.put(id, now);

        int evo = SyncEvoBridge.evo(p);

        // Double-tap F within window & Evo 3+ => Crescendo
        if (now - last <= DOUBLE_TAP_WINDOW_MS && evo >= 3) {
            triggerCrescendo(p);
        }
        // Single tap F does nothing – abilities are on SHIFT
    }

    // ----------------------------------------------------------------------
    // /syncblade echo
    // ----------------------------------------------------------------------
    public void triggerEchoStep(Player p) {
        if (!SyncAccessBridge.canUseSync(p)) return;
        long now = System.currentTimeMillis();
        SyncPlayerData d = plugin.data(p);
        int evo = SyncEvoBridge.evo(p);

        // Hunger gate
        if (p.getFoodLevel() <= ECHO_HUNGER_COST) {
            sendAB(p, ChatColor.RED + "Too exhausted to Echo.");
            pulse(p);
            return;
        }

        if (now < d.getEchoReadyAt()) {
            sendAB(p, ChatColor.YELLOW + "Echo Step cooling down...");
            pulse(p);
            return;
        }

        // Spend hunger
        p.setFoodLevel(Math.max(0, p.getFoodLevel() - ECHO_HUNGER_COST));

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

        // During Crescendo, Echo CD is sharply reduced
        if (now < d.getCrescendoActiveUntil()) {
            cd = (long) (cd * 0.3); // 70% faster while ult is up
        }

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

        // Hunger gate
        if (p.getFoodLevel() <= REVERB_HUNGER_COST) {
            sendAB(p, ChatColor.RED + "Too exhausted to Reverb.");
            pulse(p);
            return;
        }

        if (now < d.getReverbReadyAt()) {
            sendAB(p, ChatColor.YELLOW + "Reverb cooling down...");
            pulse(p);
            return;
        }

        // Spend hunger
        p.setFoodLevel(Math.max(0, p.getFoodLevel() - REVERB_HUNGER_COST));

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

        // Flow / hit requirement: need at least 5 Rhythm stacks
        if (d.getRhythmStacks() < 5) {
            sendAB(p, ChatColor.RED + "You must build at least 5 Rhythm to Crescendo.");
            pulse(p);
            return;
        }

        // Hunger gate
        if (p.getFoodLevel() <= CRESCENDO_HUNGER_COST) {
            sendAB(p, ChatColor.RED + "Too exhausted to Crescendo.");
            pulse(p);
            return;
        }

        if (now < d.getCrescendoReadyAt()) {
            sendAB(p, ChatColor.YELLOW + "Crescendo cooling down...");
            pulse(p);
            return;
        }

        // Spend hunger
        p.setFoodLevel(Math.max(0, p.getFoodLevel() - CRESCENDO_HUNGER_COST));

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
