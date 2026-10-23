package me.cortex.voxy.commonImpl.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.DataLayer;

//Shared protocol definitions for server -> client LoD streaming
//
//Flow:
// 1. Client registers the S2C channels (handled by fabric), server sees this and sends CONFIG
// 2. Client sends REQUEST batches of (chunkPos, lastKnownTimestamp) in a spiral around itself
// 3. Server answers each requested position with exactly one CHUNK packet (data, unchanged or missing)
public final class VoxyNetworking {
    private VoxyNetworking() {}

    //2: added STATUS_PUSH (server initiated updates of changed chunks)
    public static final int PROTOCOL_VERSION = 2;

    public static final ResourceLocation CONFIG = new ResourceLocation("voxy", "lod_config");
    public static final ResourceLocation REQUEST = new ResourceLocation("voxy", "lod_request");
    public static final ResourceLocation CHUNK = new ResourceLocation("voxy", "lod_chunk");

    public static final int MAX_REQUESTS_PER_PACKET = 128;
    //Vanilla limit for clientbound custom payloads is 1MiB, leave some headroom
    public static final int MAX_PACKET_SIZE = 1_000_000;

    public static final byte STATUS_DATA = 0;
    public static final byte STATUS_UNCHANGED = 1;
    public static final byte STATUS_MISSING = 2;
    //Unrequested update of a chunk that changed on the server, does not answer a request
    public static final byte STATUS_PUSH = 3;

    public static final byte LIGHT_NONE = 0;
    public static final byte LIGHT_UNIFORM = 1;
    public static final byte LIGHT_FULL = 2;

    public static final long UNKNOWN_TIMESTAMP = Long.MIN_VALUE;

    public static void writeLight(FriendlyByteBuf buf, DataLayer layer) {
        if (layer == null) {
            buf.writeByte(LIGHT_NONE);
            return;
        }
        byte[] data = layer.getData();
        //Check if the layer is a single value, if so only send that value
        byte first = data[0];
        boolean uniform = ((first>>4)&0xF) == (first&0xF);
        for (int i = 1; uniform && i < data.length; i++) {
            uniform = data[i] == first;
        }
        if (uniform) {
            buf.writeByte(LIGHT_UNIFORM);
            buf.writeByte(first&0xF);
        } else {
            buf.writeByte(LIGHT_FULL);
            buf.writeBytes(data, 0, DataLayer.SIZE);
        }
    }

    public static DataLayer readLight(FriendlyByteBuf buf) {
        byte mode = buf.readByte();
        if (mode == LIGHT_NONE) {
            return null;
        } else if (mode == LIGHT_UNIFORM) {
            return new DataLayer(buf.readByte()&0xF);
        } else if (mode == LIGHT_FULL) {
            byte[] data = new byte[DataLayer.SIZE];
            buf.readBytes(data);
            return new DataLayer(data);
        }
        throw new IllegalStateException("Unknown light mode " + mode);
    }
}
