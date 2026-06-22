package fr.yorick.localservermod.mixin;

import fr.yorick.localservermod.SimpleServerMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonBaseBlock.class)
public abstract class PistonBlockMixin {
    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private static void chestSignLock$makeProtectedBlocksImmovable(
        BlockState state,
        Level world,
        BlockPos pos,
        Direction direction,
        boolean canBreak,
        Direction pistonDirection,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (SimpleServerMod.isPistonProtected(world, pos)) {
            cir.setReturnValue(false);
        }
    }
}
