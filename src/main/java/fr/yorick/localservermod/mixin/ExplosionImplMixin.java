package fr.yorick.localservermod.mixin;

import fr.yorick.localservermod.SimpleServerMod;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.explosion.ExplosionImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ExplosionImpl.class)
public abstract class ExplosionImplMixin {
    @Inject(method = "destroyBlocks", at = @At("HEAD"))
    private void localServerMod$protectPrivateChests(List<BlockPos> blocks, CallbackInfo ci) {
        ExplosionImpl explosion = (ExplosionImpl) (Object) this;
        blocks.removeIf(pos -> SimpleServerMod.isExplosionProtected(explosion.getWorld(), pos));
    }
}
