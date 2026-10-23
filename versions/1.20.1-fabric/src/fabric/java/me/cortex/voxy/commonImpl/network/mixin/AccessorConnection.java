package me.cortex.voxy.commonImpl.network.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//Used by the LoD streamer to look at the player's network buffer and adapt its send rate
@Mixin(Connection.class)
public interface AccessorConnection {
    @Accessor("channel")
    Channel voxy$getChannel();
}
