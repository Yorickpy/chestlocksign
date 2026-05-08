package fr.yorick.localservermod.mixin;

import fr.yorick.localservermod.SimpleServerMod;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.Hopper;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {
    @Inject(method = "extract(Lnet/minecraft/world/World;Lnet/minecraft/block/entity/Hopper;)Z", at = @At("HEAD"), cancellable = true)
    private static void localServerMod$blockExtractionFromPrivateChest(
        World world,
        Hopper hopper,
        CallbackInfoReturnable<Boolean> cir
    ) {
        BlockPos inputPos = BlockPos.ofFloored(
            hopper.getHopperX(),
            hopper.getHopperY() + 1.0D,
            hopper.getHopperZ()
        );

        if (SimpleServerMod.isAutomationProtectedInventory(world, inputPos)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getInventoryAt(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/inventory/Inventory;", at = @At("HEAD"), cancellable = true)
    private static void localServerMod$hidePrivateChestFromHoppers(
        World world,
        BlockPos pos,
        CallbackInfoReturnable<Inventory> cir
    ) {
        if (SimpleServerMod.isAutomationProtectedInventory(world, pos)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "getInventoryAt(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;DDD)Lnet/minecraft/inventory/Inventory;", at = @At("HEAD"), cancellable = true)
    private static void localServerMod$hidePrivateChestFromHopperExtraction(
        World world,
        BlockPos pos,
        BlockState state,
        double x,
        double y,
        double z,
        CallbackInfoReturnable<Inventory> cir
    ) {
        if (SimpleServerMod.isAutomationProtectedInventory(world, pos)) {
            cir.setReturnValue(null);
        }
    }
}
