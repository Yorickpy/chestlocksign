package fr.yorick.localservermod.mixin;

import fr.yorick.localservermod.SimpleServerMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ServerExplosion.class)
public abstract class ExplosionImplMixin {
    @Inject(method = "interactWithBlocks", at = @At("HEAD"))
    private void localServerMod$protectPrivateChests(List<BlockPos> blocks, CallbackInfo ci) {
        ServerExplosion explosion = (ServerExplosion) (Object) this;
        blocks.removeIf(pos -> SimpleServerMod.isExplosionProtected(explosion.level(), pos));
    }
}
