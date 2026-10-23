package me.cortex.voxy.commonImpl.network;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.cortex.voxy.common.Logger;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

//Server side configuration for LoD streaming, stored in config/voxy-server.json
public class ServerLodConfig {
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .excludeFieldsWithModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.TRANSIENT)
            .create();

    public static ServerLodConfig CONFIG = loadOrCreate();

    //Master switch for sending LoDs to clients
    public boolean enabled = true;
    //Also stream LoDs when running as the integrated (singleplayer/LAN) server
    public boolean enabledOnIntegratedServer = true;
    //Maximum radius in chunks around a player that LoD data will be sent for
    public int maxRadiusChunks = 256;
    //Maximum number of chunk reads started per server tick, shared between all players
    public int maxChunkReadsPerTick = 48;
    //Maximum number of chunk requests a client may have outstanding at any time
    public int maxInFlightPerPlayer = 96;
    //Maximum (uncompressed) bytes of LoD data sent to a single player per second
    public int maxBytesPerSecondPerPlayer = 2_000_000;
    //Worker threads used for decoding/encoding chunks from disk
    public int workerThreads = 2;

    private static ServerLodConfig loadOrCreate() {
        var path = getConfigPath();
        ServerLodConfig config = null;
        if (Files.exists(path)) {
            try {
                config = GSON.fromJson(Files.readString(path), ServerLodConfig.class);
            } catch (Exception e) {
                Logger.error("Failed to load voxy server config, resetting", e);
            }
        }
        if (config == null) {
            config = new ServerLodConfig();
        }
        config.sanitize();
        config.save();
        return config;
    }

    private void sanitize() {
        this.maxRadiusChunks = Math.max(0, Math.min(this.maxRadiusChunks, 4096));
        this.maxChunkReadsPerTick = Math.max(1, this.maxChunkReadsPerTick);
        this.maxInFlightPerPlayer = Math.max(1, Math.min(this.maxInFlightPerPlayer, 1024));
        this.maxBytesPerSecondPerPlayer = Math.max(16_384, this.maxBytesPerSecondPerPlayer);
        this.workerThreads = Math.max(1, Math.min(this.workerThreads, 16));
    }

    public void save() {
        try {
            Files.writeString(getConfigPath(), GSON.toJson(this));
        } catch (Exception e) {
            Logger.error("Failed to write voxy server config", e);
        }
    }

    private static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("voxy-server.json");
    }
}
