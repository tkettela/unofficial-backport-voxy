package me.cortex.voxy.client.network;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.cortex.voxy.client.VoxyClientInstance;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static me.cortex.voxy.commonImpl.network.VoxyNetworking.*;

//Client side of LoD streaming, requests chunks in a spiral around the player (outside of the vanilla render distance)
// and feeds the received sections into the voxy ingest service as if they were loaded normally
public final class ClientLodReceiver implements net.fabricmc.api.ClientModInitializer {
    //Registered as an extra fabric "client" entrypoint
    @Override
    public void onInitializeClient() {
        init();
    }

    private static final int CACHE_FORMAT_VERSION = 1;
    private static final long CACHE_SAVE_INTERVAL_MS = 60_000;
    private static final long MISSING_RETRY_INTERVAL_MS = 300_000;//Not too often, every missing position costs the server a disk lookup

    //Per dimension streaming state
    private static final class DimensionSession {
        private final WorldIdentifier worldId;
        private final ResourceLocation dimension;
        private final Registry<Biome> biomeRegistry;
        private final boolean hasSkyLight;
        private final Path cacheFile;

        //Chunk position -> server timestamp of the data we have, guarded by itself
        private final Long2LongOpenHashMap timestamps = new Long2LongOpenHashMap();
        //Positions already requested this session, main thread only
        private final LongOpenHashSet requested = new LongOpenHashSet();
        private final AtomicInteger inFlight = new AtomicInteger();
        //Positions the server did not have yet (not generated), retried on the next spiral pass
        private final java.util.concurrent.ConcurrentLinkedQueue<Long> missing = new java.util.concurrent.ConcurrentLinkedQueue<>();
        private long spiralFinishedAt = -1;
        private volatile boolean dirty;
        private long lastSave = System.currentTimeMillis();

        //Spiral iteration state
        private int centerX, centerZ;
        private int ring, ringIndex;
        private boolean spiralStarted;

        private DimensionSession(WorldIdentifier worldId, ResourceLocation dimension, Registry<Biome> biomeRegistry, boolean hasSkyLight, Path cacheFile) {
            this.worldId = worldId;
            this.dimension = dimension;
            this.biomeRegistry = biomeRegistry;
            this.hasSkyLight = hasSkyLight;
            this.cacheFile = cacheFile;
            this.timestamps.defaultReturnValue(UNKNOWN_TIMESTAMP);
        }

        private void restartSpiral(int x, int z) {
            this.centerX = x;
            this.centerZ = z;
            this.ring = 0;
            this.ringIndex = 0;
            this.spiralStarted = true;
        }

        //Returns the next position offset in the square spiral or null if past the radius
        private long nextOffset(int radius) {
            if (this.ring > radius) {
                return Long.MAX_VALUE;
            }
            int r = this.ring;
            int dx, dz;
            if (r == 0) {
                dx = 0; dz = 0;
                this.ring++;
                this.ringIndex = 0;
            } else {
                int side = this.ringIndex / (2 * r);
                int off = this.ringIndex % (2 * r);
                switch (side) {
                    case 0 -> {dx = -r + off; dz = -r;}
                    case 1 -> {dx = r; dz = -r + off;}
                    case 2 -> {dx = r - off; dz = r;}
                    default -> {dx = -r; dz = r - off;}
                }
                if (++this.ringIndex >= 8 * r) {
                    this.ring++;
                    this.ringIndex = 0;
                }
            }
            return ChunkPos.asLong(dx, dz);
        }
    }

    private static volatile boolean serverSupported;
    private static volatile int serverRadius;
    private static volatile int serverMaxInFlight;
    private static volatile DimensionSession session;

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(CONFIG, ClientLodReceiver::onConfig);
        ClientPlayNetworking.registerGlobalReceiver(CHUNK, ClientLodReceiver::onChunk);
        ClientTickEvents.END_CLIENT_TICK.register(ClientLodReceiver::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        me.cortex.voxy.client.VoxyClient.EXTRA_DEBUG_LINES.add(ClientLodReceiver::getDebugString);
    }

    public static boolean isActive() {
        return serverSupported && session != null;
    }

    public static String getDebugString() {
        var s = session;
        if (!serverSupported || s == null) return null;
        int known;
        synchronized (s.timestamps) {
            known = s.timestamps.size();
        }
        return "Server LoD: " + (receivedRate / 1024) + " KB/s, " + s.inFlight.get() + "/" + window + " in flight, " + s.requested.size() + " requested, " + known + " cached, " + pushedChunks.get() + " live updates, radius " + serverRadius;
    }

    private static void onConfig(Minecraft client, ClientPacketListener handler, FriendlyByteBuf buf, PacketSender sender) {
        int version = buf.readVarInt();
        if (version != PROTOCOL_VERSION) {
            Logger.warn("Server uses an incompatible voxy LoD protocol version (" + version + ", expected " + PROTOCOL_VERSION + "), not using server LoDs");
            return;
        }
        int radius = buf.readVarInt();
        int maxInFlight = buf.readVarInt();
        client.execute(() -> {
            serverRadius = radius;
            serverMaxInFlight = Math.max(1, maxInFlight);
            window = Math.min(64, serverMaxInFlight);
            serverSupported = true;
            Logger.info("Server supports voxy LoD streaming, radius " + radius + " chunks");
        });
    }

    private static void reset() {
        serverSupported = false;
        closeSession();
    }

    private static void closeSession() {
        var s = session;
        session = null;
        if (s != null) {
            saveCache(s);
        }
    }

    private static void tick(Minecraft client) {
        if (!serverSupported) return;
        var level = client.level;
        var player = client.player;
        if (level == null || player == null) return;

        if (!VoxyConfig.CONFIG.serverLodEnabled || !VoxyConfig.CONFIG.ingestEnabled) {
            closeSession();
            return;
        }
        var instance = VoxyCommon.getInstance();
        if (!(instance instanceof VoxyClientInstance clientInstance)) {
            closeSession();
            return;
        }

        //Create or switch the session when the dimension or world changes
        var worldId = WorldIdentifier.of(level);
        if (worldId == null) return;
        var s = session;
        if (s == null || !s.worldId.equals(worldId)) {
            closeSession();
            if (!instance.isIngestEnabled(worldId)) return;
            var cacheFile = clientInstance.getStorageBasePath().resolve("server_lod_cache").resolve(worldId.getWorldId() + ".bin");
            s = new DimensionSession(worldId, level.dimension().location(), level.registryAccess().registryOrThrow(Registries.BIOME), level.dimensionType().hasSkyLight(), cacheFile);
            loadCache(s);
            session = s;
        }

        int px = player.chunkPosition().x;
        int pz = player.chunkPosition().z;
        boolean retryMissing = s.spiralFinishedAt != -1 && System.currentTimeMillis() - s.spiralFinishedAt > MISSING_RETRY_INTERVAL_MS;
        if (!s.spiralStarted || retryMissing || Math.abs(px - s.centerX) > 2 || Math.abs(pz - s.centerZ) > 2) {
            //Allow chunks the server did not have last time to be requested again, they may have been generated since
            Long missingPos;
            while ((missingPos = s.missing.poll()) != null) {
                s.requested.remove((long) missingPos);
            }
            s.restartSpiral(px, pz);
            s.spiralFinishedAt = -1;
        }

        int radius = Math.min(Math.min(serverRadius, VoxyConfig.CONFIG.serverLodRadius), (int) (VoxyConfig.CONFIG.sectionRenderDistance * 32));
        //Chunks inside the vanilla render distance get ingested by voxy normally
        int vanillaRadius = client.options.getEffectiveRenderDistance();

        updateWindow(s, instance);

        FriendlyByteBuf buf = null;
        int count = 0;
        int countIndex = 0;
        int scanned = 0;
        long radiusSq = (long) radius * radius;
        while (s.inFlight.get() < window && count < MAX_REQUESTS_PER_PACKET && scanned++ < 16384) {
            long offset = s.nextOffset(radius);
            if (offset == Long.MAX_VALUE) {
                if (s.spiralFinishedAt == -1) {
                    s.spiralFinishedAt = System.currentTimeMillis();
                }
                break;
            }
            int dx = ChunkPos.getX(offset);
            int dz = ChunkPos.getZ(offset);
            if ((long) dx * dx + (long) dz * dz > radiusSq) continue;
            //Voxy only ingests vanilla chunks that have all their neighbours loaded, so the outer rings of the vanilla
            // render distance never get ingested, request those from the server too (the inner part is ingested normally)
            if (Math.max(Math.abs(dx), Math.abs(dz)) < vanillaRadius - VANILLA_EDGE_RINGS) continue;
            long pos = ChunkPos.asLong(s.centerX + dx, s.centerZ + dz);
            if (!s.requested.add(pos)) continue;

            long known;
            synchronized (s.timestamps) {
                known = s.timestamps.get(pos);
            }
            if (buf == null) {
                buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeVarInt(PROTOCOL_VERSION);
                buf.writeResourceLocation(s.dimension);
                countIndex = buf.writerIndex();
                buf.writeInt(0);//Placeholder for the count, written as a fixed size varint below
            }
            buf.writeLong(pos);
            buf.writeLong(known);
            s.inFlight.incrementAndGet();
            count++;
        }

        if (buf != null) {
            //Write the count as a 4 byte padded varint so it fits the placeholder
            buf.setByte(countIndex, (count & 0x7F) | 0x80);
            buf.setByte(countIndex + 1, ((count >>> 7) & 0x7F) | 0x80);
            buf.setByte(countIndex + 2, ((count >>> 14) & 0x7F) | 0x80);
            buf.setByte(countIndex + 3, (count >>> 21) & 0x7F);
            if (ClientPlayNetworking.canSend(REQUEST)) {
                ClientPlayNetworking.send(REQUEST, buf);
            } else {
                serverSupported = false;
            }
        }

        if (s.dirty && System.currentTimeMillis() - s.lastSave > CACHE_SAVE_INTERVAL_MS) {
            saveCache(s);
        }

        if (DEBUG && System.currentTimeMillis() - lastDebugLog > 5000) {
            lastDebugLog = System.currentTimeMillis();
            Logger.info(getDebugString() + ", received " + receivedChunks.get() + " chunks with data, " + receivedOther.get() + " without");
        }
    }

    private static final boolean DEBUG = VoxyCommon.isVerificationFlagOn("serverLodDebug");
    private static long lastDebugLog;
    private static final AtomicInteger receivedChunks = new AtomicInteger();
    private static final AtomicInteger receivedOther = new AtomicInteger();
    private static final AtomicInteger pushedChunks = new AtomicInteger();

    //Called from the netty thread, decodes the sections and hands them straight to the ingest service
    //Adapt how many requests we keep outstanding to how fast voxy can convert what we receive:
    // grow while the ingest queue is small and we use the whole window, shrink when the ingest queue backs up
    private static void updateWindow(DimensionSession s, me.cortex.voxy.commonImpl.VoxyInstance instance) {
        long now = System.currentTimeMillis();
        if (now - lastWindowUpdate < 500) return;
        lastWindowUpdate = now;

        long bytes = receivedBytes.getAndSet(0);
        receivedRate = bytes * 1000 / Math.max(1, now - lastRateSample);
        lastRateSample = now;

        int backlog = instance.getIngestService().getTaskCount();
        if (backlog > INGEST_BACKLOG_HIGH) {
            window = Math.max(MIN_WINDOW, window * 3 / 4);
        } else if (backlog < INGEST_BACKLOG_LOW && s.inFlight.get() >= window - 8) {
            window = Math.min(serverMaxInFlight, window + 32);
        }
        window = Math.max(1, Math.min(window, serverMaxInFlight));
    }

    private static final int MIN_WINDOW = 16;
    //How many chunk rings at the edge of the vanilla render distance are also requested from the server
    private static final int VANILLA_EDGE_RINGS = 3;
    //Ingest backlog is counted in sections (24 per overworld chunk)
    private static final int INGEST_BACKLOG_HIGH = 12_000;
    private static final int INGEST_BACKLOG_LOW = 4_000;
    private static volatile int window = 64;
    private static long lastWindowUpdate;
    private static long lastRateSample = System.currentTimeMillis();
    private static volatile long receivedRate;
    private static final java.util.concurrent.atomic.AtomicLong receivedBytes = new java.util.concurrent.atomic.AtomicLong();

    private static void onChunk(Minecraft client, ClientPacketListener handler, FriendlyByteBuf buf, PacketSender sender) {
        receivedBytes.addAndGet(buf.readableBytes());
        var dimension = buf.readResourceLocation();
        long pos = buf.readLong();
        byte status = buf.readByte();
        long timestamp = buf.readLong();

        var s = session;
        if (s == null || !s.dimension.equals(dimension)) return;//Stale response from a previous dimension/session
        if (status == STATUS_PUSH) {
            //Server initiated update of a chunk that changed, does not answer one of our requests
            pushedChunks.incrementAndGet();
        } else {
            s.inFlight.updateAndGet(v -> Math.max(0, v - 1));
            if (status != STATUS_DATA) {
                if (status == STATUS_MISSING) {
                    s.missing.add(pos);
                }
                receivedOther.incrementAndGet();
                return;
            }
            receivedChunks.incrementAndGet();
        }

        var instance = VoxyCommon.getInstance();
        if (instance == null) return;
        var engine = instance.getOrCreate(s.worldId);
        if (engine == null) return;

        int x = ChunkPos.getX(pos);
        int z = ChunkPos.getZ(pos);
        int minSection = buf.readVarInt();
        int count = buf.readVarInt();
        try {
            for (int i = 0; i < count; i++) {
                LevelChunkSection section = new LevelChunkSection(s.biomeRegistry);
                if (buf.readBoolean()) {
                    section.read(buf);
                }
                DataLayer blockLight = readLight(buf);
                DataLayer skyLight = readLight(buf);
                if (skyLight == null && s.hasSkyLight) {
                    //Sections without stored sky light are above the terrain, so fully lit
                    skyLight = new DataLayer(15);
                }
                if (!VoxelIngestService.rawIngest(engine, section, x, minSection + i, z, blockLight, skyLight)) {
                    return;
                }
            }
        } catch (Exception e) {
            Logger.error("Failed to decode LoD chunk " + x + "," + z + " from server", e);
            return;
        }

        synchronized (s.timestamps) {
            s.timestamps.put(pos, timestamp);
        }
        s.dirty = true;
    }

    private static void loadCache(DimensionSession s) {
        if (!Files.exists(s.cacheFile)) return;
        try (var in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(s.cacheFile))))) {
            if (in.readInt() != CACHE_FORMAT_VERSION) return;
            int count = in.readInt();
            synchronized (s.timestamps) {
                for (int i = 0; i < count; i++) {
                    s.timestamps.put(in.readLong(), in.readLong());
                }
            }
        } catch (Exception e) {
            Logger.warn("Failed to read server LoD cache, all chunks will be requested again: " + e);
            synchronized (s.timestamps) {
                s.timestamps.clear();
            }
        }
    }

    private static void saveCache(DimensionSession s) {
        if (!s.dirty) return;
        s.dirty = false;
        s.lastSave = System.currentTimeMillis();
        long[] data;
        synchronized (s.timestamps) {
            data = new long[s.timestamps.size() * 2];
            int i = 0;
            for (var entry : s.timestamps.long2LongEntrySet()) {
                data[i++] = entry.getLongKey();
                data[i++] = entry.getLongValue();
            }
        }
        var file = s.cacheFile;
        Util.ioPool().execute(() -> {
            try {
                Files.createDirectories(file.getParent());
                var tmp = file.resolveSibling(file.getFileName() + ".tmp");
                try (var out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp))))) {
                    out.writeInt(CACHE_FORMAT_VERSION);
                    out.writeInt(data.length / 2);
                    for (long value : data) {
                        out.writeLong(value);
                    }
                }
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception e) {
                Logger.warn("Failed to save server LoD cache: " + e);
            }
        });
    }
}
