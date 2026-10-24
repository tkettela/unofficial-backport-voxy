package me.cortex.voxy.client.network;

import com.mojang.blaze3d.platform.InputConstants;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import me.cortex.voxy.client.core.util.IrisUtil;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

//Key to show/hide voxy lod rendering without opening the settings (idea from voxy-extra's toggle keybind)
public final class VoxyToggleKey implements net.fabricmc.api.ClientModInitializer {
    private static KeyMapping toggleKey;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.voxy.toggle_rendering",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                "key.categories.voxy"
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.consumeClick()) {
                toggleRendering(client);
            }
        });
    }

    private static void toggleRendering(Minecraft client) {
        var config = VoxyConfig.CONFIG;
        if (!config.enabled) return;
        config.enableRendering = !config.enableRendering;
        var holder = (IGetVoxyRenderSystem) client.levelRenderer;
        if (holder != null) {
            if (config.enableRendering) {
                holder.voxy$createRenderer();
            } else {
                holder.voxy$shutdownRenderer();
            }
        }
        try { IrisUtil.reload(); } catch (Throwable ignored) {}
        config.save();
        if (client.player != null) {
            client.player.displayClientMessage(Component.translatable(config.enableRendering ? "voxy.toggle.on" : "voxy.toggle.off"), true);
        }
    }
}
