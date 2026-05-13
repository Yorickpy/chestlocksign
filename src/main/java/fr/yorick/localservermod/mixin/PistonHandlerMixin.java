package fr.yorick.localservermod.mixin;

import fr.yorick.localservermod.SimpleServerMod;
import net.minecraft.block.piston.PistonHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(PistonHandler.class)
public abstract class PistonHandlerMixin {
    @Shadow
    @Final
    private World world;

    @Shadow
    public abstract List<BlockPos> getMovedBlocks();

    @Shadow
    public abstract List<BlockPos> getBrokenBlocks();

    @Inject(method = "calculatePush", at = @At("RETURN"), cancellable = true)
    private void chestSignLock$blockProtectedBlocks(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            return;
        }

        if (containsProtectedBlock(getMovedBlocks()) || containsProtectedBlock(getBrokenBlocks())) {
            cir.setReturnValue(false);
        }
    }

    private boolean containsProtectedBlock(List<BlockPos> positions) {
        return positions.stream().anyMatch(pos -> SimpleServerMod.isPistonProtected(world, pos));
    }
}
