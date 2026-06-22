package fr.yorick.localservermod.mixin;

import fr.yorick.localservermod.SimpleServerMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DoorBlock.class)
public abstract class DoorBlockMixin {
    @Inject(method = "neighborChanged", at = @At("HEAD"), cancellable = true)
    private void localServerMod$blockRedstoneForPrivateDoors(
        BlockState state,
        Level world,
        BlockPos pos,
        Block sourceBlock,
        Orientation wireOrientation,
        boolean notify,
        CallbackInfo ci
    ) {
        if (SimpleServerMod.isRedstoneProtectedBlock(world, pos)) {
            ci.cancel();
        }
    }
}
