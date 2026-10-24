package me.cortex.voxy.commonImpl.network.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

//Vanilla race: when a chunk is loaded while its save is still pending, the IO worker hands out the very tag it is
// about to write, and upgradeChunkTag edits it in place (__context) which makes that save fail with a
// ConcurrentModificationException. Rare in vanilla, frequent when lods generate/load many chunks, so always upgrade a copy.
@Mixin(ChunkStorage.class)
public class MixinChunkStorageCopy {
    @ModifyVariable(method = "upgradeChunkTag(Lnet/minecraft/resources/ResourceKey;Ljava/util/function/Supplier;Lnet/minecraft/nbt/CompoundTag;Ljava/util/Optional;)Lnet/minecraft/nbt/CompoundTag;", at = @At("HEAD"), argsOnly = true)
    private CompoundTag voxy$upgradeACopy(CompoundTag chunkData) {
        return chunkData.copy();
    }
}
