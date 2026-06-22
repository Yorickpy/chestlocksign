package fr.yorick.localservermod.mixin;

import fr.yorick.localservermod.SimpleServerMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {
    @Inject(method = "suckInItems(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/entity/Hopper;)Z", at = @At("HEAD"), cancellable = true)
    private static void localServerMod$blockExtractionFromPrivateChest(
        Level world,
        Hopper hopper,
        CallbackInfoReturnable<Boolean> cir
    ) {
        BlockPos inputPos = BlockPos.containing(
            hopper.getLevelX(),
            hopper.getLevelY() + 1.0D,
            hopper.getLevelZ()
        );

        if (SimpleServerMod.isAutomationProtectedInventory(world, inputPos)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getContainerAt(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/Container;", at = @At("HEAD"), cancellable = true)
    private static void localServerMod$hidePrivateChestFromHoppers(
        Level world,
        BlockPos pos,
        CallbackInfoReturnable<Container> cir
    ) {
        if (SimpleServerMod.isAutomationProtectedInventory(world, pos)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "getContainerAt(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;DDD)Lnet/minecraft/world/Container;", at = @At("HEAD"), cancellable = true)
    private static void localServerMod$hidePrivateChestFromHopperExtraction(
        Level world,
        BlockPos pos,
        BlockState state,
        double x,
        double y,
        double z,
        CallbackInfoReturnable<Container> cir
    ) {
        if (SimpleServerMod.isAutomationProtectedInventory(world, pos)) {
            cir.setReturnValue(null);
        }
    }
}
