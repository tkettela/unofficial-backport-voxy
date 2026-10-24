package me.cortex.voxy.commonImpl.network;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import me.cortex.voxy.common.Logger;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

//Saves the server timestamp cache (what is stored on disk for each chunk) when the server stops, and loads it on start.
// The file is deleted once loaded: after a crash there is no file, so a cache that could be outdated is never used.
final class ServerLodCachePersistence {
    private ServerLodCachePersistence() {}

    private static final int FORMAT_VERSION = 1;

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("voxy_lod_cache.bin");
    }

    static void load(MinecraftServer server) {
        var path = file(server);
        if (!Files.exists(path)) return;
        int total = 0;
        try (var in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(path))))) {
            if (in.readInt() != FORMAT_VERSION) return;
            int dimensions = in.readInt();
            for (int d = 0; d < dimensions; d++) {
                var dimension = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(in.readUTF()));
                int count = in.readInt();
                var entries = new Long2LongOpenHashMap(count);
                for (int i = 0; i < count; i++) {
                    entries.put(in.readLong(), in.readLong());
                }
                ServerLodStreamer.restoreTimestampCache(dimension, entries);
                total += count;
            }
            Logger.info("Loaded voxy LoD cache with " + total + " chunks");
        } catch (Exception e) {
            Logger.warn("Failed to load the voxy LoD cache, it will be rebuilt: " + e);
            ServerLodStreamer.clearTimestampCache();
        } finally {
            try {
                Files.deleteIfExists(path);
            } catch (Exception ignored) {}
        }
    }

    //Called on the server thread while stopping
    static void save(MinecraftServer server, Map<ResourceKey<Level>, Long2LongOpenHashMap> cache) {
        var path = file(server);
        try {
            Files.createDirectories(path.getParent());
            var tmp = path.resolveSibling(path.getFileName() + ".tmp");
            int total = 0;
            synchronized (ServerLodStreamer.class) {
                try (var out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp))))) {
                    out.writeInt(FORMAT_VERSION);
                    out.writeInt(cache.size());
                    for (var entry : cache.entrySet()) {
                        out.writeUTF(entry.getKey().location().toString());
                        //Only keep real timestamps, "missing" answers are cheap to recheck and may change
                        var kept = new Long2LongOpenHashMap();
                        for (var e : entry.getValue().long2LongEntrySet()) {
                            if (!ServerLodStreamer.isCachedMissing(e.getLongValue())) kept.put(e.getLongKey(), e.getLongValue());
                        }
                        out.writeInt(kept.size());
                        for (var e : kept.long2LongEntrySet()) {
                            out.writeLong(e.getLongKey());
                            out.writeLong(e.getLongValue());
                        }
                        total += kept.size();
                    }
                }
            }
            Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Logger.info("Saved voxy LoD cache with " + total + " chunks");
        } catch (Exception e) {
            Logger.warn("Failed to save the voxy LoD cache: " + e);
        }
    }
}
