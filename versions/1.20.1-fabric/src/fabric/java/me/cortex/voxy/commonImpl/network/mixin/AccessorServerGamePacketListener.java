package me.cortex.voxy.commonImpl.network.mixin;

import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//Used by the LoD streamer to look at the player's network buffer and adapt its send rate
@Mixin(ServerGamePacketListenerImpl.class)
public interface AccessorServerGamePacketListener {
    @Accessor("connection")
    Connection voxy$getConnection();
}
