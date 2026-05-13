package fr.yorick.localservermod.mixin;

import fr.yorick.localservermod.SimpleServerMod;
import net.minecraft.block.BlockState;
import net.minecraft.block.PistonBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonBlock.class)
public abstract class PistonBlockMixin {
    @Inject(method = "isMovable", at = @At("HEAD"), cancellable = true)
    private static void chestSignLock$makeProtectedBlocksImmovable(
        BlockState state,
        World world,
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
