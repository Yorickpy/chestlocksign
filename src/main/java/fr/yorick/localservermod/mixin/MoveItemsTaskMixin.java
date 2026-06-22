package fr.yorick.localservermod.mixin;

import fr.yorick.localservermod.SimpleServerMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.behavior.TransportItemsBetweenContainers;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TransportItemsBetweenContainers.TransportItemTarget.class)
public abstract class MoveItemsTaskMixin {
    @Inject(method = "tryCreatePossibleTarget(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/ai/behavior/TransportItemsBetweenContainers$TransportItemTarget;", at = @At("HEAD"), cancellable = true)
    private static void localServerMod$hidePrivateChestFromCopperGolem(
        BlockPos pos,
        Level world,
        CallbackInfoReturnable<TransportItemsBetweenContainers.TransportItemTarget> cir
    ) {
        if (SimpleServerMod.isAutomationProtectedInventory(world, pos)) {
            cir.setReturnValue(null);
        }
    }
}
