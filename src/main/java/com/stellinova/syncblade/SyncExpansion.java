package com.stellinova.syncblade;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SyncExpansion extends PlaceholderExpansion {

    private final SyncBladePlugin plugin;

    public SyncExpansion(SyncBladePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "sync";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Stellinova";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player p, @NotNull String params) {
        if (p == null) return "";
        SyncPlayerData d = plugin.data(p);

        return switch (params.toLowerCase()) {
            case "evo" -> String.valueOf(SyncEvoBridge.evo(p));
            case "rhythm" -> String.valueOf(d.getRhythmStacks());
            case "echo_cd" -> String.valueOf(Math.max(0, d.getEchoReadyAt() - System.currentTimeMillis()));
            case "reverb_cd" -> String.valueOf(Math.max(0, d.getReverbReadyAt() - System.currentTimeMillis()));
            case "crescendo_cd" -> String.valueOf(Math.max(0, d.getCrescendoReadyAt() - System.currentTimeMillis()));
            default -> "";
        };
    }
}
