package fr.yorick.localservermod.mixin;

import fr.yorick.localservermod.SimpleServerMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(PistonStructureResolver.class)
public abstract class PistonHandlerMixin {
    @Shadow
    @Final
    private Level level;

    @Shadow
    public abstract List<BlockPos> getToPush();

    @Shadow
    public abstract List<BlockPos> getToDestroy();

    @Inject(method = "resolve", at = @At("RETURN"), cancellable = true)
    private void chestSignLock$blockProtectedBlocks(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            return;
        }

        if (containsProtectedBlock(getToPush()) || containsProtectedBlock(getToDestroy())) {
            cir.setReturnValue(false);
        }
    }

    private boolean containsProtectedBlock(List<BlockPos> positions) {
        return positions.stream().anyMatch(pos -> SimpleServerMod.isPistonProtected(level, pos));
    }
}
