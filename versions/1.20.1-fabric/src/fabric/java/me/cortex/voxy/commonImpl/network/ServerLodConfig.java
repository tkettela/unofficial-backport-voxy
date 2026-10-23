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
// The streamer adapts to the network, the client and the server load by itself, the limits here are only upper bounds.
public class ServerLodConfig {
    private static final int CURRENT_VERSION = 2;

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .excludeFieldsWithModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.TRANSIENT)
            .create();

    public static ServerLodConfig CONFIG = loadOrCreate();

    //Missing in old config files, so it defaults to 0 for them and they get migrated
    public int configVersion = 0;
    //Master switch for sending LoDs to clients
    public boolean enabled = true;
    //Also stream LoDs when running as the integrated (singleplayer/LAN) server
    public boolean enabledOnIntegratedServer = true;
    //Maximum radius in chunks around a player that LoD data will be sent for
    public int maxRadiusChunks = 256;
    //Upper bound of chunk reads started per server tick (shared between players), lowered automatically when the server lags
    public int maxChunkReadsPerTick = 256;
    //Upper bound of chunk requests a client may have outstanding, clients lower it themselves if they cannot keep up
    public int maxInFlightPerPlayer = 512;
    //Upper bound of (uncompressed) bytes sent to a single player per second, 0 = only limited by the player's connection
    public int maxBytesPerSecondPerPlayer = 0;
    //Worker threads used for decoding/encoding chunks from disk, 0 = automatic
    public int workerThreads = 0;
    //Server tick time (ms) above which LoD streaming slows down to keep the server smooth
    public int targetTickTimeMs = 40;

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
            config.configVersion = CURRENT_VERSION;
        }
        config.migrate();
        config.sanitize();
        config.save();
        return config;
    }

    private void migrate() {
        if (this.configVersion < 2) {
            //Version 1 had fixed limits, switch them to the adaptive defaults
            Logger.info("Migrating voxy server config to adaptive streaming");
            var defaults = new ServerLodConfig();
            this.maxChunkReadsPerTick = defaults.maxChunkReadsPerTick;
            this.maxInFlightPerPlayer = defaults.maxInFlightPerPlayer;
            this.maxBytesPerSecondPerPlayer = defaults.maxBytesPerSecondPerPlayer;
            this.workerThreads = defaults.workerThreads;
            this.targetTickTimeMs = defaults.targetTickTimeMs;
        }
        this.configVersion = CURRENT_VERSION;
    }

    private void sanitize() {
        this.maxRadiusChunks = Math.max(0, Math.min(this.maxRadiusChunks, 4096));
        this.maxChunkReadsPerTick = Math.max(1, this.maxChunkReadsPerTick);
        this.maxInFlightPerPlayer = Math.max(1, Math.min(this.maxInFlightPerPlayer, 2048));
        this.maxBytesPerSecondPerPlayer = Math.max(0, this.maxBytesPerSecondPerPlayer);
        this.workerThreads = Math.max(0, Math.min(this.workerThreads, 32));
        this.targetTickTimeMs = Math.max(10, Math.min(this.targetTickTimeMs, 200));
    }

    public int getWorkerThreads() {
        if (this.workerThreads > 0) return this.workerThreads;
        return Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors() / 3));
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
