package com.stellinova.syncblade;

import java.util.UUID;

/**
 * Per-player runtime state for SyncBlade.
 * Focused on rhythm stacks, primed hits (Echo / Reverb), and cooldown timers.
 */
public class SyncPlayerData {

    private final UUID id;

    // Rhythm Flow
    private int rhythmStacks;
    private long lastHitAt;
    private long lastMissAt;

    // Echo Step
    private long echoReadyAt;
    private boolean echoPrimed;
    private long echoPrimedUntil;

    // Reverb Strike
    private long reverbReadyAt;
    private boolean reverbPrimed;
    private long reverbHitWindowUntil;

    // Crescendo ultimate (Evo 3 only)
    private long crescendoReadyAt;
    private long crescendoActiveUntil;
    private long crescendoNextTickAt;

    public SyncPlayerData(UUID id) {
        this.id = id;
    }

    public UUID getId() { return id; }

    // Rhythm
    public int getRhythmStacks() { return rhythmStacks; }
    public void setRhythmStacks(int rhythmStacks) { this.rhythmStacks = rhythmStacks; }

    public long getLastHitAt() { return lastHitAt; }
    public void setLastHitAt(long lastHitAt) { this.lastHitAt = lastHitAt; }

    public long getLastMissAt() { return lastMissAt; }
    public void setLastMissAt(long lastMissAt) { this.lastMissAt = lastMissAt; }

    // Echo Step
    public long getEchoReadyAt() { return echoReadyAt; }
    public void setEchoReadyAt(long echoReadyAt) { this.echoReadyAt = echoReadyAt; }

    public boolean isEchoPrimed() { return echoPrimed; }
    public void setEchoPrimed(boolean echoPrimed) { this.echoPrimed = echoPrimed; }

    public long getEchoPrimedUntil() { return echoPrimedUntil; }
    public void setEchoPrimedUntil(long echoPrimedUntil) { this.echoPrimedUntil = echoPrimedUntil; }

    // Reverb Strike
    public long getReverbReadyAt() { return reverbReadyAt; }
    public void setReverbReadyAt(long reverbReadyAt) { this.reverbReadyAt = reverbReadyAt; }

    public boolean isReverbPrimed() { return reverbPrimed; }
    public void setReverbPrimed(boolean reverbPrimed) { this.reverbPrimed = reverbPrimed; }

    public long getReverbHitWindowUntil() { return reverbHitWindowUntil; }
    public void setReverbHitWindowUntil(long reverbHitWindowUntil) { this.reverbHitWindowUntil = reverbHitWindowUntil; }

    // Crescendo
    public long getCrescendoReadyAt() { return crescendoReadyAt; }
    public void setCrescendoReadyAt(long crescendoReadyAt) { this.crescendoReadyAt = crescendoReadyAt; }

    public long getCrescendoActiveUntil() { return crescendoActiveUntil; }
    public void setCrescendoActiveUntil(long crescendoActiveUntil) { this.crescendoActiveUntil = crescendoActiveUntil; }

    public long getCrescendoNextTickAt() { return crescendoNextTickAt; }
    public void setCrescendoNextTickAt(long crescendoNextTickAt) { this.crescendoNextTickAt = crescendoNextTickAt; }
}
