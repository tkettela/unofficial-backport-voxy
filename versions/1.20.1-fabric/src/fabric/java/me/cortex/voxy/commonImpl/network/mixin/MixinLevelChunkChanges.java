package me.cortex.voxy.commonImpl.network.mixin;

import me.cortex.voxy.commonImpl.network.ServerChunkChangeTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

//Records server side block changes so the LoD streamer can push the changed chunks to clients
@Mixin(LevelChunk.class)
public class MixinLevelChunkChanges {
    @Shadow @Final Level level;

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void voxy$trackChange(BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> cir) {
        //A null return means nothing changed
        if (cir.getReturnValue() != null && this.level instanceof ServerLevel serverLevel) {
            ServerChunkChangeTracker.markChanged(serverLevel.dimension(), ((LevelChunk) (Object) this).getPos().toLong());
        }
    }
}
