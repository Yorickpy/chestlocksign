package fr.yorick.localservermod.mixin;

import fr.yorick.localservermod.SimpleServerMod;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.block.WireOrientation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DoorBlock.class)
public abstract class DoorBlockMixin {
    @Inject(method = "neighborUpdate", at = @At("HEAD"), cancellable = true)
    private void localServerMod$blockRedstoneForPrivateDoors(
        BlockState state,
        World world,
        BlockPos pos,
        Block sourceBlock,
        WireOrientation wireOrientation,
        boolean notify,
        CallbackInfo ci
    ) {
        if (SimpleServerMod.isRedstoneProtectedBlock(world, pos)) {
            ci.cancel();
        }
    }
}
