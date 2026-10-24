package me.cortex.voxy.client.config;

import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import me.cortex.voxy.client.core.SSAO;
import me.cortex.voxy.client.core.gl.Capabilities;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.cpu.CpuLayout;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.minecraft.client.Minecraft;

import java.util.Locale;

//"Voxy impact" presets: one choice that sets all the heavy settings at once, details stay editable (-> CUSTOM).
// AUTO picks a level from the hardware (gpu memory, cpu cores, java heap) and takes whatever lod radius the server offers.
public final class VoxyPresets {
    private VoxyPresets() {}

    public enum Preset {AUTO, LOW, MEDIUM, HIGH, ULTRA, CUSTOM}

    public static Preset current(VoxyConfig config) {
        if (config.preset == null) return Preset.CUSTOM;
        try {
            return Preset.valueOf(config.preset.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return Preset.CUSTOM;
        }
    }

    //Called when a detail setting is changed by hand
    public static void markCustom(VoxyConfig config) {
        config.preset = Preset.CUSTOM.name().toLowerCase(Locale.ROOT);
    }

    //Level AUTO resolves to on this computer
    public static Preset detect() {
        int cores = CpuLayout.getCoreCount();
        long heapGb = Runtime.getRuntime().maxMemory() / (1024L * 1024 * 1024);
        long vramGb = -1;
        try {
            var caps = Capabilities.INSTANCE;
            if (caps.canQueryGpuMemory && caps.totalDedicatedMemory > 0) {
                vramGb = caps.totalDedicatedMemory / (1024L * 1024 * 1024);
            }
        } catch (Throwable ignored) {}
        //Unknown gpu memory (amd/intel do not report it): judge from the cpu and heap only
        long vram = vramGb < 0 ? 6 : vramGb;

        if (vram < 4 || cores < 4 || heapGb < 3) return Preset.LOW;
        if (vram < 6 || cores < 6 || heapGb < 4) return Preset.MEDIUM;
        if (vram < 10 || cores < 12 || heapGb < 6) return Preset.HIGH;
        return Preset.ULTRA;
    }

    //Applies the values of a preset to the config, CUSTOM keeps everything as is
    public static void apply(VoxyConfig config, Preset preset) {
        config.preset = preset.name().toLowerCase(Locale.ROOT);
        if (preset == Preset.CUSTOM) return;
        Preset level = preset == Preset.AUTO ? detect() : preset;
        int cores = Math.max(1, CpuLayout.getCoreCount());
        switch (level) {
            case LOW -> {
                config.sectionRenderDistance = 4;//128 chunks
                config.subDivisionSize = 128;
                config.serviceThreads = Math.max(1, cores / 4);
                config.setSSAOMode(SSAO.SSAOMode.BASIC);
                config.serverLodRadius = 128;
            }
            case MEDIUM -> {
                config.sectionRenderDistance = 8;//256 chunks
                config.subDivisionSize = 96;
                config.serviceThreads = Math.max(1, cores / 3);
                config.setSSAOMode(SSAO.SSAOMode.AUTO);
                config.serverLodRadius = 256;
            }
            case HIGH -> {
                config.sectionRenderDistance = 16;//512 chunks
                config.subDivisionSize = 64;
                config.serviceThreads = (int) Math.max(1, cores / 1.5);
                config.setSSAOMode(SSAO.SSAOMode.BETTER);
                config.serverLodRadius = 512;
            }
            default -> {
                config.sectionRenderDistance = 32;//1024 chunks
                config.subDivisionSize = 40;
                config.serviceThreads = Math.max(1, cores - 2);
                config.setSSAOMode(SSAO.SSAOMode.BEST);
                config.serverLodRadius = 1024;
            }
        }
        if (preset == Preset.AUTO) {
            //Take whatever radius the server offers, it is still limited by the voxy render distance
            config.serverLodRadius = 1024;
        }
        Logger.info("Applied voxy preset " + preset + (preset == Preset.AUTO ? " (" + level + " for this computer)" : ""));
    }

    //Applies a preset chosen in game and pushes the new values to what is running
    public static void applyLive(VoxyConfig config, Preset preset) {
        var previousSsao = config.getSSAOMode();
        apply(config, preset);
        var instance = VoxyCommon.getInstance();
        if (instance != null) {
            instance.updateDedicatedThreads();
        }
        var holder = (IGetVoxyRenderSystem) Minecraft.getInstance().levelRenderer;
        if (holder != null) {
            var renderSystem = holder.voxy$getRenderSystem();
            if (renderSystem != null) {
                renderSystem.setRenderDistance(config.sectionRenderDistance);
                if (previousSsao != config.getSSAOMode() && config.isRenderingEnabled()) {
                    holder.voxy$shutdownRenderer();
                    holder.voxy$createRenderer();
                }
            }
        }
    }
}
