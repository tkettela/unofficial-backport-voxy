package me.cortex.voxy.client.config;

import com.google.common.collect.ImmutableList;
import me.cortex.voxy.client.ClientSessionEvents;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import me.cortex.voxy.client.core.SSAO;
import me.cortex.voxy.client.core.util.IrisUtil;
import me.cortex.voxy.client.mixin.sodium.AccessorSodiumWorldRenderer;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.caffeinemc.mods.sodium.client.gui.options.*;
import net.caffeinemc.mods.sodium.client.gui.options.control.CyclingControl;
import net.caffeinemc.mods.sodium.client.gui.options.control.SliderControl;
import net.caffeinemc.mods.sodium.client.gui.options.control.TickBoxControl;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public abstract class VoxyConfigScreenPages {
//? if 1.20.1 {
    private static final Component[] SSAO_MODE_LABELS = {
            Component.translatable("voxy.config.general.ssao_mode.auto"),
            Component.translatable("voxy.config.general.ssao_mode.basic"),
            Component.translatable("voxy.config.general.ssao_mode.better"),
            Component.translatable("voxy.config.general.ssao_mode.best")
    };
    private static int MAX_RENDER_DISTANCE = 64 * 16;

    private VoxyConfigScreenPages(){}

    public static OptionPage voxyOptionPage = null;

    public static OptionPage page() {
        List<OptionGroup> groups = new ArrayList<>();
        VoxyConfig storage = VoxyConfig.CONFIG;

        //"Voxy impact" preset: sets all the heavy settings at once, the other options are refreshed to show the new values
        groups.add(OptionGroup.createBuilder()
                .add(PRESET_OPTION[0] = OptionImpl.createBuilder(VoxyPresets.Preset.class, storage)
                        .setName(Component.translatable("voxy.config.preset"))
                        .setTooltip(Component.translatable("voxy.config.preset.tooltip"))
                        .setControl(opt -> new CyclingControl<>(opt, VoxyPresets.Preset.class, PRESET_LABELS))
                        .setBinding((s, v) -> {
                            VoxyPresets.applyLive(s, v);
                            refreshAllOptions(groups);
                        }, VoxyPresets::current)
                        .setImpact(OptionImpact.HIGH)
                        .build()
                ).build()
        );

        //General
        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(boolean.class, storage)
                        .setName(Component.translatable("voxy.config.general.enabled"))
                        .setTooltip(Component.translatable("voxy.config.general.enabled.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((s, v)->{
                            s.enabled = v;
                            if (v && ClientSessionEvents.inSession) {
                                VoxyCommon.createInstance();
                            }

                            if (!v) {
                                var vrsh = (IGetVoxyRenderSystem) Minecraft.getInstance().levelRenderer;
                                if (vrsh != null) {
                                    vrsh.voxy$shutdownRenderer();
                                }
                                VoxyCommon.shutdownInstance();
                            }

                            try { IrisUtil.reload(); } catch (Throwable ignored) {}
                        }, s -> s.enabled)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build()
                ).build()
        );

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(int.class, storage)
                        .setName(Component.translatable("voxy.config.general.serviceThreads"))
                        .setTooltip(Component.translatable("voxy.config.general.serviceThreads.tooltip"))
                        .setControl(opt->new SliderControl(opt, 1,
                                // CpuLayout.CORES.length, //Just do core size as max
                                Runtime.getRuntime().availableProcessors() * 2,//Note: this is threads not cores, the default value is half the core count, is fine as this should technically be the limit but CpuLayout.CORES.length is more realistic
                                1, v->Component.literal(Integer.toString(v))))
                        .setBinding((s, v)->{
                            s.serviceThreads = v;
                            markCustom(s);
                            var instance = VoxyCommon.getInstance();
                            if (instance != null) {
                                instance.updateDedicatedThreads();
                            }
                        }, s -> s.serviceThreads)
                        .setImpact(OptionImpact.HIGH)
                        .build()
                ).add(OptionImpl.createBuilder(boolean.class, storage)
                        .setName(Component.translatable("voxy.config.general.useSodiumBuilder"))
                        .setTooltip(Component.translatable("voxy.config.general.useSodiumBuilder.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.VARIES)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .setBinding((s, v) -> {
                            s.dontUseSodiumBuilderThreads = !v;
                            var instance = VoxyCommon.getInstance();
                            if (instance != null) {
                                instance.updateDedicatedThreads();
                            }
                        }, s->!s.dontUseSodiumBuilderThreads)
                        .build()
                ).add(OptionImpl.createBuilder(boolean.class, storage)
                        .setName(Component.translatable("voxy.config.general.ingest"))
                        .setTooltip(Component.translatable("voxy.config.general.ingest.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((s, v) -> s.ingestEnabled = v, s -> s.ingestEnabled)
                        .setImpact(OptionImpact.MEDIUM)
                        .build()
                ).add(OptionImpl.createBuilder(boolean.class, storage)
                        .setName(Component.translatable("voxy.config.general.serverLod"))
                        .setTooltip(Component.translatable("voxy.config.general.serverLod.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((s, v) -> s.serverLodEnabled = v, s -> s.serverLodEnabled)
                        .setImpact(OptionImpact.MEDIUM)
                        .build()
                ).add(OptionImpl.createBuilder(int.class, storage)
                        .setName(Component.translatable("voxy.config.general.serverLodRadius"))
                        .setTooltip(Component.translatable("voxy.config.general.serverLodRadius.tooltip"))
                        .setControl(opt->new SliderControl(opt, 2, 64, 1, v->Component.literal(Integer.toString(v*16))))
                        .setBinding((s, v) -> { s.serverLodRadius = v*16; markCustom(s); }, s -> Math.max(2, Math.min(64, s.serverLodRadius/16)))
                        .setImpact(OptionImpact.MEDIUM)
                        .build()
                ).add(OptionImpl.createBuilder(boolean.class, storage)
                        .setName(Component.translatable("voxy.config.general.lodStorageByServerName"))
                        .setTooltip(Component.translatable("voxy.config.general.lodStorageByServerName.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((s, v) -> s.lodStorageByServerName = v, s -> s.lodStorageByServerName)
                        .setImpact(OptionImpact.LOW)
                        .build()
                ).add(OptionImpl.createBuilder(boolean.class, storage)
                        .setName(Component.translatable("voxy.config.general.fixLodWaterSeams"))
                        .setTooltip(Component.translatable("voxy.config.general.fixLodWaterSeams.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((s, v) -> s.fixLodWaterSeams = v, s -> s.fixLodWaterSeams)
                        .setImpact(OptionImpact.LOW)
                        .build()
                ).add(OptionImpl.createBuilder(boolean.class, storage)
                        .setName(Component.translatable("voxy.config.general.fixNetherFog"))
                        .setTooltip(Component.translatable("voxy.config.general.fixNetherFog.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((s, v) -> s.fixNetherFog = v, s -> s.fixNetherFog)
                        .setImpact(OptionImpact.LOW)
                        .build()
                ).build()
        );

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(boolean.class, storage)
                        .setName(Component.translatable("voxy.config.general.rendering"))
                        .setTooltip(Component.translatable("voxy.config.general.rendering.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((s, v)->{
                            s.enableRendering = v;
                            var vrsh = (IGetVoxyRenderSystem)Minecraft.getInstance().levelRenderer;
                            if (vrsh != null) {
                                if (v) {
                                    vrsh.voxy$createRenderer();
                                } else {
                                    vrsh.voxy$shutdownRenderer();
                                }
                            }
                            try { IrisUtil.reload(); } catch (Throwable ignored) {}
                        }, s -> s.enableRendering)
                        .setImpact(OptionImpact.HIGH)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build()
                ).add(OptionImpl.createBuilder(int.class, storage)
                        .setName(Component.translatable("voxy.config.general.subDivisionSize"))
                        .setTooltip(Component.translatable("voxy.config.general.subDivisionSize.tooltip"))
                        .setControl(opt -> new SliderControl(opt, 0, SUBDIV_IN_MAX, 1, v -> Component.literal(Integer.toString(Math.round(ln2subDiv(v))))))
                        .setBinding((s, v) -> { s.subDivisionSize = ln2subDiv(v); markCustom(s); }, s -> subDiv2ln(s.subDivisionSize))
                        .setImpact(OptionImpact.HIGH)
                        .build()
                ).add(OptionImpl.createBuilder(int.class, storage)
                        .setName(Component.translatable("voxy.config.general.renderDistance"))
                        .setTooltip(Component.translatable("voxy.config.general.renderDistance.tooltip"))
                        // Range: 10 to MAX_RENDER_DISTANCE. Display: v*2
                        .setControl(opt -> new SliderControl(opt, 10, MAX_RENDER_DISTANCE, 1, v -> Component.literal(Integer.toString(v * 2))))
                        .setBinding((s, v) -> {
                            // Value stored as float fraction
                            s.sectionRenderDistance = (((float)v) / 16.0f);
                            markCustom(s);

                            var vrsh = (IGetVoxyRenderSystem) Minecraft.getInstance().levelRenderer;
                            if (vrsh != null) {
                                var vrs = vrsh.voxy$getRenderSystem();
                                if (vrs != null) {
                                    vrs.setRenderDistance(s.sectionRenderDistance);
                                }
                            }
                        }, s -> Math.min(MAX_RENDER_DISTANCE, Math.round(s.sectionRenderDistance * 16)))
                        .setImpact(OptionImpact.LOW)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build()
                ).build()
        );

        OptionImpl<VoxyConfig, Boolean> adaptCloudDistanceOption = OptionImpl.createBuilder(boolean.class, storage)
                .setName(Component.literal("Adapt cloud distance"))
                .setTooltip(Component.literal("Extends the cloud distance according to the current render distance. It's automatically capped at 256"))
                .setControl(TickBoxControl::new)
                .setBinding((s, v) -> s.adaptCloudDistance = v, s -> s.adaptCloudDistance)
                .setImpact(OptionImpact.LOW)
                .build();

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(boolean.class, storage)
                        .setName(Component.translatable("voxy.config.general.render_fog"))
                        .setTooltip(Component.translatable("voxy.config.general.render_fog.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((s, v) -> s.useEnvironmentalFog = v, s -> s.useEnvironmentalFog)
                        .setImpact(OptionImpact.LOW)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build()
                ).add(OptionImpl.createBuilder(int.class, storage)
                        .setName(Component.literal("Sky fog distance"))
                        .setTooltip(Component.literal("Higher distance, sharper sky fog"))
                        .setControl(opt -> new SliderControl(opt, 16, 512, 16, v -> Component.literal(Integer.toString(v))))
                        .setBinding((s, v) -> s.skyFogDistance = v, s -> s.skyFogDistance)
                        .build()
                ).add(OptionImpl.createBuilder(int.class, storage)
                        .setName(Component.literal("Fog intensity"))
                        .setTooltip(Component.literal("Multiplier for terrain fog opacity. 0.0 = off, 1.0 = vanilla, 3.0 = triple"))
                        .setControl(opt -> new SliderControl(opt, 0, 300, 10, v -> Component.literal(String.format("%.1f", v / 100.0f))))
                        .setBinding((s, v) -> s.fogIntensity = v / 100.0f, s -> (int)(s.fogIntensity * 100))
                        .build()
                ).add(OptionImpl.createBuilder(int.class, storage)
                        .setName(Component.literal("Fog curve"))
                        .setTooltip(Component.literal("Shape of the fog curve. 0.0 = linear, higher values push fog towards the far end"))
                        .setControl(opt -> new SliderControl(opt, 0, 50, 1, v -> Component.literal(String.format("%.1f", v / 10.0f))))
                        .setBinding((s, v) -> s.fogDensity = v / 10.0f, s -> (int)(s.fogDensity * 10))
                        .build()
                ).add(adaptCloudDistanceOption).add(OptionImpl.createBuilder(int.class, storage)
                        .setName(Component.literal("Cloud distance"))
                        .setTooltip(Component.literal("Cloud render distance in chunks"))
                        .setEnabled(!adaptCloudDistanceOption.getValue())
                        .setControl(opt -> new SliderControl(opt, 0, 2048, 2, v -> {
                            if (adaptCloudDistanceOption.getValue())
                                return Component.literal("Adaptive");
                            return Component.literal(v < 1 ? "Default" : Integer.toString(v));
                        }))
                        .setBinding((s, v) -> s.cloudDistance = v, s -> s.cloudDistance)
                        .setImpact(OptionImpact.VARIES)
                        .build()
                ).add(OptionImpl.createBuilder(SSAO.SSAOMode.class, storage)
                        .setName(Component.translatable("voxy.config.general.ssao_mode"))
                        .setTooltip(Component.translatable("voxy.config.general.ssao_mode.tooltip"))
                        .setControl(opt -> new CyclingControl<>(opt, SSAO.SSAOMode.class, SSAO_MODE_LABELS))
                        .setBinding((s, v) -> {
                            s.setSSAOMode(v);
                            markCustom(s);
                            reloadActiveRenderer();
                        }, VoxyConfig::getSSAOMode)
                        .setImpact(OptionImpact.HIGH)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build()
                ).build()
        );
        return new OptionPage(Component.translatable("voxy.config.title"), ImmutableList.copyOf(groups));
    }

    private static final Option<?>[] PRESET_OPTION = new Option<?>[1];

    //A detail was changed by hand: the preset becomes custom (only the preset option is refreshed, other pending changes are kept)
    private static void markCustom(VoxyConfig config) {
        if (VoxyPresets.current(config) == VoxyPresets.Preset.CUSTOM) return;
        VoxyPresets.markCustom(config);
        if (PRESET_OPTION[0] != null) {
            try { PRESET_OPTION[0].reset(); } catch (Throwable ignored) {}
        }
    }

    private static final Component[] PRESET_LABELS = {
            Component.translatable("voxy.config.preset.auto"),
            Component.translatable("voxy.config.preset.low"),
            Component.translatable("voxy.config.preset.medium"),
            Component.translatable("voxy.config.preset.high"),
            Component.translatable("voxy.config.preset.ultra"),
            Component.translatable("voxy.config.preset.custom")
    };

    //After a preset changed the config, make every option show its new value
    private static void refreshAllOptions(List<OptionGroup> groups) {
        for (var group : groups) {
            for (var option : group.getOptions()) {
                try {
                    option.reset();
                } catch (Throwable ignored) {}
            }
        }
    }

    private static void reloadActiveRenderer() {
        try {
            var minecraft = Minecraft.getInstance();
            var renderer = (IGetVoxyRenderSystem) minecraft.levelRenderer;
            if (renderer != null && minecraft.level != null && VoxyConfig.CONFIG.isRenderingEnabled()) {
                renderer.voxy$shutdownRenderer();
                renderer.voxy$createRenderer();
            }
        } catch (Throwable ignored) {}

        try { IrisUtil.reload(); } catch (Throwable ignored) {}
    }

    private static final int SUBDIV_IN_MAX = 100;
    private static final double SUBDIV_MIN = 28;
    private static final double SUBDIV_MAX = 256;
    private static final double SUBDIV_CONST = Math.log(SUBDIV_MAX/SUBDIV_MIN)/Math.log(2);


    //In range is 0->200
    //Out range is 28->256
    private static float ln2subDiv(int in) {
        return (float) (SUBDIV_MIN*Math.pow(2, SUBDIV_CONST*((double)in/SUBDIV_IN_MAX)));
    }

    //In range is ... any?
    //Out range is 0->200
    private static int subDiv2ln(float in) {
        return (int) (((Math.log(((double)in)/SUBDIV_MIN)/Math.log(2))/SUBDIV_CONST)*SUBDIV_IN_MAX);
    }
//? }
}