package me.cortex.voxy.commonImpl.network;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

//Collects the positions of chunks that had block changes on the server, so they can be pushed to LoD clients
public final class ServerChunkChangeTracker {
    private ServerChunkChangeTracker() {}

    public interface ChangeConsumer {void accept(ServerLevel level, long chunkPos);}

    private static volatile boolean enabled;
    private static final Map<ResourceKey<Level>, Set<Long>> CHANGED = new ConcurrentHashMap<>();

    static void setEnabled(boolean state) {
        enabled = state;
        if (!state) {
            CHANGED.clear();
        }
    }

    //Called from the LevelChunk mixin whenever a block state actually changed in a server level
    public static void markChanged(ResourceKey<Level> dimension, long chunkPos) {
        if (!enabled) return;
        CHANGED.computeIfAbsent(dimension, k -> ConcurrentHashMap.newKeySet()).add(chunkPos);
    }

    static void drain(MinecraftServer server, ChangeConsumer consumer) {
        if (CHANGED.isEmpty()) return;
        for (var entry : CHANGED.entrySet()) {
            var level = server.getLevel(entry.getKey());
            var set = entry.getValue();
            var positions = set.toArray(new Long[0]);
            for (Long pos : positions) {
                set.remove(pos);
                if (level != null) {
                    consumer.accept(level, pos);
                }
            }
        }
    }
}
