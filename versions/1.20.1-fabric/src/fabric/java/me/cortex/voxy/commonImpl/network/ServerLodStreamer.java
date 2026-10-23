package me.cortex.voxy.commonImpl.network;

import com.mojang.serialization.Codec;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.cortex.voxy.common.Logger;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.S2CPlayChannelEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static me.cortex.voxy.commonImpl.network.VoxyNetworking.*;

//Server side of LoD streaming, reads chunks (from memory if loaded and dirty, otherwise from the region files)
// and sends their raw section data to voxy clients which then voxelize it locally.
// Also pushes chunks that changed (block updates) to players that only see them as LoDs.
public final class ServerLodStreamer implements net.fabricmc.api.ModInitializer {
    //Registered as an extra fabric "main" entrypoint
    @Override
    public void onInitialize() {
        init();
    }

    private static final Codec<PalettedContainer<BlockState>> BLOCK_STATE_CODEC = PalettedContainer.codecRW(Block.BLOCK_STATE_REGISTRY, BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState());

    //How often (in ticks) changed chunks are pushed to players
    private static final int PUSH_INTERVAL_TICKS = 100;
    //Upper bound of cached chunk timestamps, the cache is cleared if exceeded
    private static final int MAX_CACHED_TIMESTAMPS = 4_000_000;
    private static final long CACHED_MISSING = Long.MIN_VALUE + 1;

    private record Request(ResourceKey<Level> dimension, long pos, long knownTimestamp) {}

    //A packet ready to be sent, plus what we learnt about the chunk while reading it (for the timestamp cache)
    private record Outgoing(FriendlyByteBuf buf, ResourceKey<Level> dimension, long pos, long cacheValue, int regionEpoch) {
        static final long NOT_CACHEABLE = Long.MIN_VALUE + 2;
    }

    private static final class PlayerState {
        private final ArrayDeque<Request> pending = new ArrayDeque<>();//Only accessed from the server thread
        private final ConcurrentLinkedQueue<Outgoing> outbox = new ConcurrentLinkedQueue<>();
        private final AtomicLong outboxBytes = new AtomicLong();
        private final AtomicInteger reading = new AtomicInteger();
        private long tokens;

        private void enqueue(FriendlyByteBuf buf) {
            this.enqueue(new Outgoing(buf, null, 0, Outgoing.NOT_CACHEABLE, 0));
        }

        private void enqueue(Outgoing out) {
            var buf = out.buf;
            if (buf.readableBytes() > MAX_PACKET_SIZE) {
                //Too big for a single custom payload (very tall worlds), skip it
                var dimension = buf.readResourceLocation();
                long pos = buf.readLong();
                buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeResourceLocation(dimension);
                buf.writeLong(pos);
                buf.writeByte(STATUS_MISSING);
                buf.writeLong(0);
                out = new Outgoing(buf, null, 0, Outgoing.NOT_CACHEABLE, 0);
            }
            this.outboxBytes.addAndGet(buf.readableBytes());
            this.outbox.add(out);
        }
    }

    private static final Map<UUID, PlayerState> PLAYERS = new ConcurrentHashMap<>();
    private static final java.util.Set<UUID> PENDING_ENABLE = ConcurrentHashMap.newKeySet();
    private static volatile ExecutorService WORKERS;
    private static int roundRobinOffset;
    private static int tickCounter;

    //Per dimension: chunk pos -> LastUpdate timestamp of the stored chunk (or CACHED_MISSING), lets us answer
    // "unchanged"/"missing" without touching the disk. Only accessed from the server thread.
    private static final Map<ResourceKey<Level>, Long2LongOpenHashMap> TIMESTAMP_CACHE = new HashMap<>();
    //Per dimension: region (32x32 chunks) -> counter bumped whenever a chunk in it loads/unloads, used to drop
    // cache entries that were read while the chunk could have changed. Only accessed from the server thread.
    private static final Map<ResourceKey<Level>, Long2IntOpenHashMap> REGION_EPOCHS = new HashMap<>();

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(ServerLodStreamer::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(ServerLodStreamer::onServerStopping);
        ServerTickEvents.END_SERVER_TICK.register(ServerLodStreamer::tick);

        //A loaded chunk can change, so forget what we knew about it from disk
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk) -> invalidate(level, chunk.getPos().toLong()));
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> invalidate(level, chunk.getPos().toLong()));

        ServerPlayNetworking.registerGlobalReceiver(REQUEST, ServerLodStreamer::onRequest);
        //The client tells us what channels it can receive, once it says it can receive LoD chunks enable streaming for it
        // this can fire while the connection is still being set up, so only mark the player and enable it next tick
        S2CPlayChannelEvents.REGISTER.register((handler, sender, server, channels) -> {
            if (channels.contains(CHUNK)) {
                PENDING_ENABLE.add(handler.player.getUUID());
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> PENDING_ENABLE.add(handler.player.getUUID()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PLAYERS.remove(handler.player.getUUID());
            PENDING_ENABLE.remove(handler.player.getUUID());
        });
    }

    private static boolean isEnabled(MinecraftServer server) {
        var config = ServerLodConfig.CONFIG;
        return config.enabled && config.maxRadiusChunks > 0 && (server.isDedicatedServer() || config.enabledOnIntegratedServer);
    }

    private static void onServerStarted(MinecraftServer server) {
        if (!isEnabled(server)) {
            Logger.info("Voxy server LoD streaming disabled");
            return;
        }
        int threads = ServerLodConfig.CONFIG.workerThreads;
        AtomicInteger counter = new AtomicInteger();
        WORKERS = Executors.newFixedThreadPool(threads, r -> {
            var thread = new Thread(r, "Voxy LoD Streaming Worker #" + counter.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY + 1);
            return thread;
        });
        ServerChunkChangeTracker.setEnabled(true);
        Logger.info("Voxy server LoD streaming enabled, radius " + ServerLodConfig.CONFIG.maxRadiusChunks + " chunks");
    }

    private static void onServerStopping(MinecraftServer server) {
        ServerChunkChangeTracker.setEnabled(false);
        PLAYERS.clear();
        PENDING_ENABLE.clear();
        synchronized (ServerLodStreamer.class) {
            TIMESTAMP_CACHE.clear();
            REGION_EPOCHS.clear();
        }
        var workers = WORKERS;
        WORKERS = null;
        if (workers != null) {
            workers.shutdownNow();
            try {
                workers.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
        }
    }

    private static long regionKey(long pos) {
        return ChunkPos.asLong(ChunkPos.getX(pos) >> 5, ChunkPos.getZ(pos) >> 5);
    }

    private static synchronized int regionEpoch(ResourceKey<Level> dimension, long pos) {
        var epochs = REGION_EPOCHS.get(dimension);
        return epochs == null ? 0 : epochs.get(regionKey(pos));
    }

    private static synchronized void invalidate(ServerLevel level, long pos) {
        var cache = TIMESTAMP_CACHE.get(level.dimension());
        if (cache != null) {
            cache.remove(pos);
        }
        REGION_EPOCHS.computeIfAbsent(level.dimension(), k -> new Long2IntOpenHashMap()).addTo(regionKey(pos), 1);
    }

    private static void tryEnable(MinecraftServer server, ServerPlayer player) {
        if (WORKERS == null || !isEnabled(server)) return;
        if (PLAYERS.containsKey(player.getUUID())) return;
        if (!ServerPlayNetworking.canSend(player, CONFIG) || !ServerPlayNetworking.canSend(player, CHUNK)) return;

        PLAYERS.put(player.getUUID(), new PlayerState());

        var buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(PROTOCOL_VERSION);
        buf.writeVarInt(ServerLodConfig.CONFIG.maxRadiusChunks);
        buf.writeVarInt(ServerLodConfig.CONFIG.maxInFlightPerPlayer);
        ServerPlayNetworking.send(player, CONFIG, buf);
        Logger.info("Enabled LoD streaming for " + player.getGameProfile().getName());
    }

    //Called from the netty thread
    private static void onRequest(MinecraftServer server, ServerPlayer player, ServerGamePacketListenerImpl handler, FriendlyByteBuf buf, PacketSender sender) {
        int version = buf.readVarInt();
        if (version != PROTOCOL_VERSION) return;
        var dimension = ResourceKey.create(Registries.DIMENSION, buf.readResourceLocation());
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_REQUESTS_PER_PACKET) {
            Logger.warn("Player " + player.getGameProfile().getName() + " sent an invalid LoD request");
            return;
        }
        var requests = new Request[count];
        for (int i = 0; i < count; i++) {
            requests[i] = new Request(dimension, buf.readLong(), buf.readLong());
        }
        var uuid = player.getUUID();
        server.execute(() -> {
            var state = PLAYERS.get(uuid);
            if (state == null) return;
            //Hard cap to protect against misbehaving clients
            int cap = ServerLodConfig.CONFIG.maxInFlightPerPlayer * 4;
            for (var request : requests) {
                if (state.pending.size() >= cap) break;
                state.pending.add(request);
            }
        });
    }

    private static void tick(MinecraftServer server) {
        if (!PENDING_ENABLE.isEmpty()) {
            for (var uuid : PENDING_ENABLE.toArray(new UUID[0])) {
                PENDING_ENABLE.remove(uuid);
                var player = server.getPlayerList().getPlayer(uuid);
                if (player == null) continue;
                try {
                    tryEnable(server, player);
                } catch (Exception e) {
                    Logger.error("Failed to enable LoD streaming for " + player.getGameProfile().getName(), e);
                }
            }
        }
        var workers = WORKERS;
        if (workers == null) return;
        if (PLAYERS.isEmpty()) {
            //Nobody to push to, dont let changes pile up
            ServerChunkChangeTracker.drain(server, (level, pos) -> {});
            return;
        }
        var config = ServerLodConfig.CONFIG;

        if (++tickCounter >= PUSH_INTERVAL_TICKS) {
            tickCounter = 0;
            pushChangedChunks(server);
        }

        var states = PLAYERS.entrySet().toArray(new Map.Entry[0]);

        //Send what is ready, rate limited per player
        long refill = Math.max(1, config.maxBytesPerSecondPerPlayer / 20);
        for (var entry : states) {
            var state = (PlayerState) entry.getValue();
            var player = server.getPlayerList().getPlayer((UUID) entry.getKey());
            if (player == null) {
                PLAYERS.remove((UUID) entry.getKey());
                continue;
            }
            state.tokens = Math.min(state.tokens + refill, config.maxBytesPerSecondPerPlayer);
            while (state.tokens > 0) {
                var out = state.outbox.poll();
                if (out == null) break;
                int size = out.buf.readableBytes();
                state.tokens -= size;
                state.outboxBytes.addAndGet(-size);
                rememberTimestamp(server, out);
                ServerPlayNetworking.send(player, CHUNK, out.buf);
            }
        }

        //Start new reads, round robin between players so one player cannot starve the others
        int budget = config.maxChunkReadsPerTick;
        long maxBacklog = config.maxBytesPerSecondPerPlayer * 2L;
        boolean progress = true;
        roundRobinOffset++;
        while (budget > 0 && progress) {
            progress = false;
            for (int i = 0; i < states.length && budget > 0; i++) {
                var entry = states[(i + roundRobinOffset) % states.length];
                var state = (PlayerState) entry.getValue();
                if (state.pending.isEmpty()) continue;
                //Backpressure: dont read more if the player cannot receive it fast enough
                if (state.outboxBytes.get() > maxBacklog || state.reading.get() >= config.maxInFlightPerPlayer) continue;
                var player = server.getPlayerList().getPlayer((UUID) entry.getKey());
                if (player == null) continue;
                //Answers from the timestamp cache dont cost a disk read
                if (process(player, state, state.pending.poll(), workers)) {
                    budget--;
                }
                progress = true;
            }
        }
    }

    //Called on the server thread right before a packet is sent, records what was read from disk into the cache
    // if nothing could have changed the chunk since it was read
    private static synchronized void rememberTimestamp(MinecraftServer server, Outgoing out) {
        if (out.cacheValue == Outgoing.NOT_CACHEABLE || out.dimension == null) return;
        if (regionEpoch(out.dimension, out.pos) != out.regionEpoch) return;
        var level = server.getLevel(out.dimension);
        if (level == null || level.getChunkSource().getChunkNow(ChunkPos.getX(out.pos), ChunkPos.getZ(out.pos)) != null) return;
        var cache = TIMESTAMP_CACHE.computeIfAbsent(out.dimension, k -> {
            var map = new Long2LongOpenHashMap();
            map.defaultReturnValue(UNKNOWN_TIMESTAMP);
            return map;
        });
        if (cache.size() >= MAX_CACHED_TIMESTAMPS) {
            cache.clear();
        }
        cache.put(out.pos, out.cacheValue);
    }

    private static FriendlyByteBuf header(ResourceKey<Level> dimension, long pos, byte status, long timestamp) {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeResourceLocation(dimension.location());
        buf.writeLong(pos);
        buf.writeByte(status);
        buf.writeLong(timestamp);
        return buf;
    }

    //Returns true if a disk read was started
    private static boolean process(ServerPlayer player, PlayerState state, Request request, ExecutorService workers) {
        ServerLevel level = player.serverLevel();
        if (!level.dimension().equals(request.dimension)) {
            state.enqueue(header(request.dimension, request.pos, STATUS_MISSING, 0));
            return false;
        }

        int cx = ChunkPos.getX(request.pos);
        int cz = ChunkPos.getZ(request.pos);
        int dx = cx - player.chunkPosition().x;
        int dz = cz - player.chunkPosition().z;
        long maxRadius = ServerLodConfig.CONFIG.maxRadiusChunks + 2;
        if ((long) dx * dx + (long) dz * dz > maxRadius * maxRadius) {
            state.enqueue(header(request.dimension, request.pos, STATUS_MISSING, 0));
            return false;
        }

        //If the chunk is loaded and has unsaved changes the disk copy is outdated, send it straight from memory
        LevelChunk loaded = level.getChunkSource().getChunkNow(cx, cz);
        if (loaded != null && loaded.isUnsaved()) {
            state.enqueue(encodeLoaded(level, loaded, STATUS_DATA));
            return false;
        }

        long known = request.knownTimestamp;
        if (loaded == null) {
            long cached;
            synchronized (ServerLodStreamer.class) {
                var cache = TIMESTAMP_CACHE.get(request.dimension);
                cached = cache == null ? UNKNOWN_TIMESTAMP : cache.get(request.pos);
            }
            if (cached == CACHED_MISSING) {
                state.enqueue(header(request.dimension, request.pos, STATUS_MISSING, 0));
                return false;
            }
            if (cached != UNKNOWN_TIMESTAMP && known != UNKNOWN_TIMESTAMP && cached <= known) {
                state.enqueue(header(request.dimension, request.pos, STATUS_UNCHANGED, cached));
                return false;
            }
        }

        var biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
        int minSection = level.getMinSection();
        int sectionCount = level.getSectionsCount();
        var dimension = request.dimension;
        int epoch = regionEpoch(dimension, request.pos);

        state.reading.incrementAndGet();
        level.getChunkSource().chunkMap.readChunk(new ChunkPos(cx, cz)).handleAsync((tag, error) -> {
            try {
                if (error != null) {
                    Logger.warn("Failed to read chunk " + cx + "," + cz + " for LoD streaming: " + error);
                    state.enqueue(header(dimension, request.pos, STATUS_MISSING, 0));
                } else {
                    state.enqueue(encodeStored(dimension, request.pos, known, tag, biomeRegistry, minSection, sectionCount, epoch));
                }
            } catch (Throwable e) {
                Logger.error("Failed to encode chunk " + cx + "," + cz + " for LoD streaming", e);
                state.enqueue(header(dimension, request.pos, STATUS_MISSING, 0));
            } finally {
                state.reading.decrementAndGet();
            }
            return null;
        }, workers);
        return true;
    }

    //Push chunks that had block changes to the players that only see them as LoDs (outside their view distance)
    private static void pushChangedChunks(MinecraftServer server) {
        int viewDistance = server.getPlayerList().getViewDistance();
        long maxRadius = ServerLodConfig.CONFIG.maxRadiusChunks;
        long maxBacklog = ServerLodConfig.CONFIG.maxBytesPerSecondPerPlayer * 2L;
        ServerChunkChangeTracker.drain(server, (level, pos) -> {
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(pos), ChunkPos.getZ(pos));
            if (chunk == null) return;//Unloaded since, it will be read from disk with its new timestamp when requested
            FriendlyByteBuf encoded = null;
            for (var entry : PLAYERS.entrySet()) {
                var player = server.getPlayerList().getPlayer(entry.getKey());
                if (player == null || player.serverLevel() != level) continue;
                var state = entry.getValue();
                if (state.outboxBytes.get() > maxBacklog) continue;//Player is already behind, it will catch up on reconnect
                int dx = ChunkPos.getX(pos) - player.chunkPosition().x;
                int dz = ChunkPos.getZ(pos) - player.chunkPosition().z;
                //Inside the view distance the client gets the chunk (and voxy ingests it) the vanilla way
                if (Math.max(Math.abs(dx), Math.abs(dz)) <= viewDistance + 1) continue;
                if ((long) dx * dx + (long) dz * dz > maxRadius * maxRadius) continue;
                if (encoded == null) {
                    encoded = encodeLoaded(level, chunk, STATUS_PUSH);
                }
                state.enqueue(new FriendlyByteBuf(encoded.copy()));
            }
        });
    }

    private static FriendlyByteBuf encodeLoaded(ServerLevel level, LevelChunk chunk, byte status) {
        var pos = chunk.getPos();
        var buf = header(level.dimension(), pos.toLong(), status, level.getGameTime());
        var lightEngine = level.getLightEngine();
        var blockLight = lightEngine.getLayerListener(LightLayer.BLOCK);
        var skyLight = lightEngine.getLayerListener(LightLayer.SKY);
        var sections = chunk.getSections();
        int minSection = level.getMinSection();
        buf.writeVarInt(minSection);
        buf.writeVarInt(sections.length);
        for (int i = 0; i < sections.length; i++) {
            var section = sections[i];
            buf.writeBoolean(section != null);
            if (section != null) {
                section.write(buf);
            }
            var sectionPos = SectionPos.of(pos, minSection + i);
            writeLight(buf, copy(blockLight.getDataLayerData(sectionPos)));
            writeLight(buf, copy(skyLight.getDataLayerData(sectionPos)));
        }
        return buf;
    }

    private static DataLayer copy(DataLayer layer) {
        return layer == null ? null : layer.copy();
    }

    private static Outgoing encodeStored(ResourceKey<Level> dimension, long pos, long knownTimestamp, Optional<CompoundTag> optionalTag, Registry<Biome> biomeRegistry, int minSection, int sectionCount, int epoch) {
        if (optionalTag.isEmpty()) {
            return new Outgoing(header(dimension, pos, STATUS_MISSING, 0), dimension, pos, CACHED_MISSING, epoch);
        }
        var tag = optionalTag.get();
        var status = ChunkStatus.byName(tag.getString("Status"));
        if (!status.isOrAfter(ChunkStatus.FEATURES)) {
            //Not generated far enough to have useful terrain
            return new Outgoing(header(dimension, pos, STATUS_MISSING, 0), dimension, pos, CACHED_MISSING, epoch);
        }

        long timestamp = tag.getLong("LastUpdate");
        if (knownTimestamp != UNKNOWN_TIMESTAMP && timestamp <= knownTimestamp) {
            return new Outgoing(header(dimension, pos, STATUS_UNCHANGED, timestamp), dimension, pos, timestamp, epoch);
        }

        boolean lightOn = tag.getBoolean("isLightOn") && status.isOrAfter(ChunkStatus.LIGHT);
        var plains = biomeRegistry.getHolderOrThrow(Biomes.PLAINS);
        Codec<PalettedContainerRO<Holder<Biome>>> biomeCodec = PalettedContainer.codecRO(biomeRegistry.asHolderIdMap(), biomeRegistry.holderByNameCodec(), PalettedContainer.Strategy.SECTION_BIOMES, plains);

        var sections = new LevelChunkSection[sectionCount];
        var blockLight = new DataLayer[sectionCount];
        var skyLight = new DataLayer[sectionCount];

        ListTag sectionList = tag.getList("sections", Tag.TAG_COMPOUND);
        for (int i = 0; i < sectionList.size(); i++) {
            CompoundTag sectionTag = sectionList.getCompound(i);
            int index = sectionTag.getByte("Y") - minSection;
            if (index < 0 || index >= sectionCount) continue;//Light only sections above/below the world

            PalettedContainer<BlockState> states;
            if (sectionTag.contains("block_states", Tag.TAG_COMPOUND)) {
                states = BLOCK_STATE_CODEC.parse(NbtOps.INSTANCE, sectionTag.getCompound("block_states")).resultOrPartial(err -> {}).orElse(null);
            } else {
                states = null;
            }
            if (states == null) {
                states = new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES);
            }

            PalettedContainerRO<Holder<Biome>> biomes;
            if (sectionTag.contains("biomes", Tag.TAG_COMPOUND)) {
                biomes = biomeCodec.parse(NbtOps.INSTANCE, sectionTag.getCompound("biomes")).resultOrPartial(err -> {}).orElse(null);
            } else {
                biomes = null;
            }
            if (biomes == null) {
                biomes = new PalettedContainer<>(biomeRegistry.asHolderIdMap(), plains, PalettedContainer.Strategy.SECTION_BIOMES);
            }

            sections[index] = new LevelChunkSection(states, biomes);

            if (lightOn) {
                if (sectionTag.contains("BlockLight", Tag.TAG_BYTE_ARRAY)) {
                    byte[] data = sectionTag.getByteArray("BlockLight");
                    if (data.length == DataLayer.SIZE) blockLight[index] = new DataLayer(data);
                }
                if (sectionTag.contains("SkyLight", Tag.TAG_BYTE_ARRAY)) {
                    byte[] data = sectionTag.getByteArray("SkyLight");
                    if (data.length == DataLayer.SIZE) skyLight[index] = new DataLayer(data);
                }
            }
        }

        var buf = header(dimension, pos, STATUS_DATA, timestamp);
        buf.writeVarInt(minSection);
        buf.writeVarInt(sectionCount);
        for (int i = 0; i < sectionCount; i++) {
            var section = sections[i];
            buf.writeBoolean(section != null);
            if (section != null) {
                section.write(buf);
            }
            writeLight(buf, blockLight[i]);
            writeLight(buf, skyLight[i]);
        }
        return new Outgoing(buf, dimension, pos, timestamp, epoch);
    }
}
