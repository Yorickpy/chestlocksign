package fr.yorick.localservermod.mixin;

import fr.yorick.localservermod.SimpleServerMod;
import net.minecraft.entity.ai.brain.task.MoveItemsTask;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MoveItemsTask.class)
public abstract class MoveItemsTaskMixin {
    @Inject(method = "matchesStoragePredicate", at = @At("HEAD"), cancellable = true)
    private void localServerMod$hidePrivateChestFromCopperGolem(
        MoveItemsTask.Storage storage,
        World world,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (SimpleServerMod.isAutomationProtectedInventory(world, storage.pos())) {
            cir.setReturnValue(false);
        }
    }
}
