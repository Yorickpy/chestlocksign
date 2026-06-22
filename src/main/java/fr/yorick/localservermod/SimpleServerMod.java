package fr.yorick.localservermod;

import com.mojang.brigadier.Command;
import com.mojang.serialization.Codec;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SignItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SimpleServerMod implements ModInitializer {
    public static final String MOD_ID = "local_server_mod";
    private static final String CONFIG_DIR_NAME = "ChestLockSign";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final int OWNER_SIGN_WATCH_TICKS = 20 * 60 * 5;
    private static final int LOCKED_MESSAGE_COOLDOWN_TICKS = 10;
    private static final String LEGACY_NAME_KEY = "n";
    private static final String LEGACY_UUID_KEY = "u";
    private static final AttachmentType<Map<String, String>> SIGN_USER_UUIDS = AttachmentRegistry.createPersistent(
        Identifier.fromNamespaceAndPath(MOD_ID, "sign_user_uuids"),
        Codec.unboundedMap(Codec.STRING, Codec.STRING)
    );
    private static final List<OwnerSignWatch> ownerSignWatches = new ArrayList<>();
    private static final Map<UUID, Integer> lastLockedMessageTicks = new HashMap<>();
    private static boolean debugLockedChest = false;
    private static Messages messages;

    @Override
    public void onInitialize() {
        messages = Messages.load();
        LOGGER.info("Chest SignLock charge.");

        registerLanguageCommand();
        registerDebugCommand();
        registerChestOpenDebugEvent();
        registerBlockBreakProtection();
        registerOwnerSignWatch();
    }

    private static void registerLanguageCommand() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("chestlocklang")
                .requires(SimpleServerMod::canUseLanguageCommand)
                .then(Commands.literal("fr")
                    .executes(context -> setLanguage(context.getSource(), "fr")))
                .then(Commands.literal("en")
                    .executes(context -> setLanguage(context.getSource(), "en"))))
        );
    }

    private static boolean canUseLanguageCommand(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player == null || canBypassLocks(player);
    }

    private static boolean canBypassLocks(ServerPlayer player) {
        return player.level()
            .getServer()
            .getPlayerList()
            .isOp(player.nameAndId());
    }

    private static int setLanguage(CommandSourceStack source, String language) {
        try {
            Messages.setLanguage(language);
            messages = Messages.load();
            source.sendSuccess(
                () -> Component.literal("Chest SignLock language set to " + language + "."),
                true
            );
            return Command.SINGLE_SUCCESS;
        } catch (IOException exception) {
            LOGGER.warn("Impossible de changer la langue Chest SignLock.", exception);
            source.sendFailure(Component.literal("Impossible de changer la langue Chest SignLock."));
            return 0;
        }
    }

    private static void registerDebugCommand() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("chestlockdebug")
                .requires(SimpleServerMod::canUseLanguageCommand)
                .executes(context -> toggleDebugMode(context.getSource())))
        );
    }

    private static int toggleDebugMode(CommandSourceStack source) {
        debugLockedChest = !debugLockedChest;
        source.sendSuccess(
            () -> Component.literal("Debug Signlock mode is " + (debugLockedChest ? "ON" : "OFF") + "."),
            true
        );
        return Command.SINGLE_SUCCESS;
    }

    private static void registerChestOpenDebugEvent() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }

            BlockPos clickedPos = hitResult.getBlockPos();
            BlockState state = world.getBlockState(clickedPos);
            InteractionResult signProtectionResult = protectPrivateChestSign(serverPlayer, world, clickedPos, state);
            if (signProtectionResult != InteractionResult.PASS) {
                return signProtectionResult;
            }

            BlockPos targetPos = clickedPos;
            if (!isLockableBlock(state)) {
                return InteractionResult.PASS;
            }

            ItemStack heldStack = serverPlayer.getItemInHand(hand);
            List<AttachedSignInfo> attachedSigns = getAttachedSigns(world, targetPos);
            hydrateSignUuidsForPlayer(world, attachedSigns, serverPlayer);
            attachedSigns = getAttachedSigns(world, targetPos);
            boolean locked = isPrivateChest(attachedSigns);
            boolean authorized = !locked || canBypassLocks(serverPlayer) || hasAccessToChest(attachedSigns, serverPlayer);

            if (!authorized) {
                sendLockedChestMessage(serverPlayer, messages.chestLocked());
                return InteractionResult.SUCCESS;
            }

            if (serverPlayer.isShiftKeyDown() || !(heldStack.getItem() instanceof SignItem signItem)) {
                if (locked && debugLockedChest) {
                    sendChestOpenLog(serverPlayer, attachedSigns);
                }
                return InteractionResult.PASS;
            }

            PrivateSignPlacement placement = placePrivateSign(
                serverPlayer,
                world,
                targetPos,
                hitResult.getDirection(),
                signItem,
                locked
            );
            if (placement.placed()) {
                sendOptionalLockedChestMessage(serverPlayer, messages.signCreated());
                consumeSignIfNeeded(serverPlayer, heldStack);

                LOGGER.info(
                    "Private sign created: player={} chestPos={} signPos={}",
                    serverPlayer.getName().getString(),
                    targetPos,
                    placement.signPos()
                );

                return InteractionResult.SUCCESS;
            }

            sendLockedChestMessage(serverPlayer, messages.cannotPlaceSign());
            return InteractionResult.SUCCESS;
        });
    }

    private static void sendOptionalLockedChestMessage(ServerPlayer player, String message) {
        if (!message.isBlank()) {
            sendLockedChestMessage(player, message);
        }
    }

    private static void sendLockedChestMessage(ServerPlayer player, String message) {
        int currentTick = player.tickCount;
        Integer lastTick = lastLockedMessageTicks.get(player.getUUID());
        if (lastTick != null && currentTick - lastTick < LOCKED_MESSAGE_COOLDOWN_TICKS) {
            return;
        }

        lastLockedMessageTicks.put(player.getUUID(), currentTick);
        player.sendSystemMessage(Component.literal("[Chest SignLock] " + message).withStyle(ChatFormatting.GOLD), false);
    }

    private static void sendChestOpenLog(ServerPlayer player, List<AttachedSignInfo> attachedSigns) {
        List<String> authorizedUsers = getAuthorizedUsers(attachedSigns);
        MutableComponent opener = Component.literal(player.getName().getString()).withStyle(ChatFormatting.GOLD);

        player.sendSystemMessage(Component.literal("[Debug Signlock] ")
            .withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.literal("Coffre ouvert par ").withStyle(ChatFormatting.GRAY))
            .append(opener), false);

        player.sendSystemMessage(Component.literal("[Debug Signlock] ")
            .withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.literal("Panneaux: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(String.valueOf(attachedSigns.size())).withStyle(ChatFormatting.AQUA))
            .append(Component.literal(" | Utilisateurs autorises: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(authorizedUsers.isEmpty() ? "aucun" : String.join(", ", authorizedUsers))
                .withStyle(ChatFormatting.GREEN)), false);
    }

    private static void registerOwnerSignWatch() {
        ServerTickEvents.END_SERVER_TICK.register(SimpleServerMod::validateWatchedOwnerSigns);
    }

    private static void registerBlockBreakProtection() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClientSide()) {
                return true;
            }

            if (isProtectedDoorSupport(world, pos)) {
                if (player instanceof ServerPlayer serverPlayer && canBypassLocks(serverPlayer)) {
                    return true;
                }

                if (player instanceof ServerPlayer serverPlayer) {
                    sendLockedChestMessage(serverPlayer, messages.chestBreakLocked());
                }

                return false;
            }

            if (isLockableBlock(state) && isPrivateChest(getAttachedSigns(world, pos))) {
                if (player instanceof ServerPlayer serverPlayer) {
                    if (canBypassLocks(serverPlayer)) {
                        return true;
                    }

                    sendLockedChestMessage(serverPlayer, messages.chestBreakLocked());
                }

                return false;
            }

            BlockPos attachedLockablePos = getLockablePosForAttachedSign(world, pos, state);
            if (attachedLockablePos == null) {
                return true;
            }

            List<AttachedSignInfo> attachedSigns = getAttachedSigns(world, attachedLockablePos);
            if (!isPrivateChest(attachedSigns)) {
                return true;
            }

            AttachedSignInfo brokenSign = getAttachedSignAt(attachedSigns, pos);
            if (brokenSign == null || !brokenSign.canGrantAccess()) {
                return true;
            }

            if (player instanceof ServerPlayer serverPlayer) {
                hydrateSignUuidsForPlayer(world, attachedSigns, serverPlayer);
                attachedSigns = getAttachedSigns(world, attachedLockablePos);
                brokenSign = getAttachedSignAt(attachedSigns, pos);
                if (brokenSign == null || !brokenSign.canGrantAccess()) {
                    return true;
                }
            }

            if (player instanceof ServerPlayer serverPlayer) {
                if (canBypassLocks(serverPlayer)) {
                    return true;
                }

                if (brokenSign.isPrivate() && !brokenSign.isOwner(serverPlayer)) {
                    sendLockedChestMessage(serverPlayer, messages.signLocked());
                    return false;
                }
            }

            if (hasAccessToChest(attachedSigns, player)) {
                return true;
            }

            if (player instanceof ServerPlayer serverPlayer) {
                sendLockedChestMessage(serverPlayer, messages.signLocked());
            }

            return false;
        });
    }

    private static InteractionResult protectPrivateChestSign(
        ServerPlayer player,
        Level world,
        BlockPos signPos,
        BlockState signState
    ) {
        BlockPos attachedLockablePos = getLockablePosForAttachedSign(world, signPos, signState);
        if (attachedLockablePos == null) {
            return InteractionResult.PASS;
        }

        List<AttachedSignInfo> attachedSigns = getAttachedSigns(world, attachedLockablePos);
        hydrateSignUuidsForPlayer(world, attachedSigns, player);
        attachedSigns = getAttachedSigns(world, attachedLockablePos);
        if (!isPrivateChest(attachedSigns)) {
            return InteractionResult.PASS;
        }

        if (canBypassLocks(player) || hasAccessToChest(attachedSigns, player)) {
            AttachedSignInfo ownerSign = getOwnerSign(attachedSigns);
            if (!canBypassLocks(player) && ownerSign != null && ownerSign.pos().equals(signPos)) {
                if (!ownerSign.isOwner(player)) {
                    sendLockedChestMessage(player, messages.ownerSignLocked());
                    return InteractionResult.SUCCESS;
                }

                watchOwnerSign(player, world, signPos, ownerSign.ownerName());
                return InteractionResult.PASS;
            }

            return InteractionResult.PASS;
        }

        sendLockedChestMessage(player, messages.signLocked());
        return InteractionResult.SUCCESS;
    }

    private static BlockPos getLockablePosForAttachedSign(Level world, BlockPos signPos, BlockState signState) {
        if (signState.getBlock() instanceof WallSignBlock && signState.hasProperty(WallSignBlock.FACING)) {
            BlockPos attachedPos = signPos.relative(signState.getValue(WallSignBlock.FACING).getOpposite());
            return isLockableBlock(world.getBlockState(attachedPos)) ? attachedPos : null;
        }

        if (signState.getBlock() instanceof SignBlock) {
            BlockPos belowPos = signPos.below();
            return isLockableBlock(world.getBlockState(belowPos)) ? belowPos : null;
        }

        return null;
    }

    public static boolean isExplosionProtected(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);

        if (isProtectedDoorSupport(world, pos)) {
            return true;
        }

        if (isLockableBlock(state)) {
            return isPrivateChest(getAttachedSigns(world, pos));
        }

        BlockPos attachedLockablePos = getLockablePosForAttachedSign(world, pos, state);
        return attachedLockablePos != null && isPrivateChest(getAttachedSigns(world, attachedLockablePos));
    }

    public static boolean isPistonProtected(Level world, BlockPos pos) {
        return isExplosionProtected(world, pos);
    }

    public static boolean isAutomationProtectedInventory(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!isAutomationLockableInventory(state)) {
            return false;
        }

        List<AttachedSignInfo> signs = getAttachedSigns(world, pos);
        return isPrivateChest(signs) && !allowsRedstone(signs);
    }

    private static void watchOwnerSign(ServerPlayer player, Level world, BlockPos signPos, String ownerName) {
        if (!(world instanceof ServerLevel serverWorld)) {
            return;
        }

        OwnerSignKey key = new OwnerSignKey(serverWorld.dimension(), signPos);
        ownerSignWatches.removeIf(watch -> watch.key().equals(key));
        ownerSignWatches.add(new OwnerSignWatch(
            key,
            ownerName,
            player.getUUID(),
            player.getName().getString(),
            OWNER_SIGN_WATCH_TICKS
        ));
    }

    private static void validateWatchedOwnerSigns(MinecraftServer server) {
        Iterator<OwnerSignWatch> iterator = ownerSignWatches.iterator();

        while (iterator.hasNext()) {
            OwnerSignWatch watch = iterator.next();
            ServerLevel world = server.getLevel(watch.key().worldKey());
            if (world == null || watch.remainingTicks() <= 0) {
                iterator.remove();
                continue;
            }

            BlockEntity blockEntity = world.getBlockEntity(watch.key().pos());
            if (!(blockEntity instanceof SignBlockEntity signBlockEntity)) {
                iterator.remove();
                continue;
            }

            SignText text = signBlockEntity.getFrontText();
            List<String> lines = readSignLines(text);
            boolean ownerChanged = lines.size() < 2
                || !"[private]".equalsIgnoreCase(lines.get(0))
                || !watch.ownerName().equalsIgnoreCase(lines.get(1));

            if (ownerChanged) {
                SignText restoredText = text
                    .setMessage(0, Component.literal("[Private]"))
                    .setMessage(1, Component.literal(watch.ownerName()));

                signBlockEntity.setText(restoredText, true);
                signBlockEntity.setChanged();
                world.getChunkSource().blockChanged(watch.key().pos());

            }

            watch.decrement();
        }
    }

    public static boolean isRedstoneProtectedBlock(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!isLockableBlock(state)) {
            return false;
        }

        List<AttachedSignInfo> signs = getAttachedSigns(world, pos);
        return isPrivateChest(signs) && !allowsRedstone(signs);
    }

    private static boolean isProtectedDoorSupport(Level world, BlockPos pos) {
        BlockPos doorPos = pos.above();
        BlockState doorState = world.getBlockState(doorPos);
        return doorState.getBlock() instanceof DoorBlock
            && doorState.hasProperty(DoorBlock.HALF)
            && doorState.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
            && isPrivateChest(getAttachedSigns(world, doorPos));
    }

    private static boolean isChest(BlockState state) {
        return state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST);
    }

    private static boolean isLockableBlock(BlockState state) {
        return isChest(state)
            || state.is(Blocks.BARREL)
            || state.getBlock() instanceof ShulkerBoxBlock
            || state.is(Blocks.DISPENSER)
            || state.is(Blocks.DROPPER)
            || state.is(Blocks.FURNACE)
            || state.is(Blocks.BLAST_FURNACE)
            || state.is(Blocks.SMOKER)
            || state.is(Blocks.LECTERN)
            || state.is(Blocks.BEACON)
            || state.getBlock() instanceof DoorBlock;
    }

    private static boolean isAutomationLockableInventory(BlockState state) {
        return isChest(state)
            || state.is(Blocks.BARREL)
            || state.getBlock() instanceof ShulkerBoxBlock
            || state.is(Blocks.DISPENSER)
            || state.is(Blocks.DROPPER)
            || state.is(Blocks.FURNACE)
            || state.is(Blocks.BLAST_FURNACE)
            || state.is(Blocks.SMOKER);
    }

    private static PrivateSignPlacement placePrivateSign(
        ServerPlayer player,
        Level world,
        BlockPos targetPos,
        Direction clickedSide,
        SignItem signItem,
        boolean moreUsersSign
    ) {
        if (clickedSide == Direction.UP) {
            return placeStandingPrivateSign(player, world, targetPos.above(), signItem, moreUsersSign);
        }

        if (!clickedSide.getAxis().isHorizontal()) {
            return PrivateSignPlacement.failed();
        }

        BlockPos signPos = targetPos.relative(clickedSide);
        if (!canPlaceWallSign(world, signPos)) {
            return PrivateSignPlacement.failed();
        }

        BlockState signState = getWallSignBlock(((BlockItem) signItem).getBlock()).defaultBlockState()
            .setValue(WallSignBlock.FACING, clickedSide);

        if (!world.setBlockAndUpdate(signPos, signState)) {
            return PrivateSignPlacement.failed();
        }

        BlockEntity blockEntity = world.getBlockEntity(signPos);
        if (blockEntity instanceof SignBlockEntity signBlockEntity) {
            writePrivateSignText(player, world, signPos, signBlockEntity, moreUsersSign);
        }

        return PrivateSignPlacement.placed(signPos);
    }

    private static PrivateSignPlacement placeStandingPrivateSign(
        ServerPlayer player,
        Level world,
        BlockPos signPos,
        SignItem signItem,
        boolean moreUsersSign
    ) {
        if (!canPlaceWallSign(world, signPos)) {
            return PrivateSignPlacement.failed();
        }

        BlockState signState = ((BlockItem) signItem).getBlock().defaultBlockState()
            .setValue(StandingSignBlock.ROTATION, getStandingSignRotation(player));

        if (!world.setBlockAndUpdate(signPos, signState)) {
            return PrivateSignPlacement.failed();
        }

        BlockEntity blockEntity = world.getBlockEntity(signPos);
        if (blockEntity instanceof SignBlockEntity signBlockEntity) {
            writePrivateSignText(player, world, signPos, signBlockEntity, moreUsersSign);
        }

        return PrivateSignPlacement.placed(signPos);
    }

    private static Block getWallSignBlock(Block standingSignBlock) {
        if (standingSignBlock == Blocks.SPRUCE_SIGN) {
            return Blocks.SPRUCE_WALL_SIGN;
        }
        if (standingSignBlock == Blocks.BIRCH_SIGN) {
            return Blocks.BIRCH_WALL_SIGN;
        }
        if (standingSignBlock == Blocks.ACACIA_SIGN) {
            return Blocks.ACACIA_WALL_SIGN;
        }
        if (standingSignBlock == Blocks.CHERRY_SIGN) {
            return Blocks.CHERRY_WALL_SIGN;
        }
        if (standingSignBlock == Blocks.JUNGLE_SIGN) {
            return Blocks.JUNGLE_WALL_SIGN;
        }
        if (standingSignBlock == Blocks.DARK_OAK_SIGN) {
            return Blocks.DARK_OAK_WALL_SIGN;
        }
        if (standingSignBlock == Blocks.PALE_OAK_SIGN) {
            return Blocks.PALE_OAK_WALL_SIGN;
        }
        if (standingSignBlock == Blocks.MANGROVE_SIGN) {
            return Blocks.MANGROVE_WALL_SIGN;
        }
        if (standingSignBlock == Blocks.BAMBOO_SIGN) {
            return Blocks.BAMBOO_WALL_SIGN;
        }
        if (standingSignBlock == Blocks.CRIMSON_SIGN) {
            return Blocks.CRIMSON_WALL_SIGN;
        }
        if (standingSignBlock == Blocks.WARPED_SIGN) {
            return Blocks.WARPED_WALL_SIGN;
        }

        return Blocks.OAK_WALL_SIGN;
    }

    private static int getStandingSignRotation(ServerPlayer player) {
        return Math.floorMod(Math.round(player.getYRot() * 16.0F / 360.0F) + 8, 16);
    }

    private static void writePrivateSignText(
        ServerPlayer player,
        Level world,
        BlockPos signPos,
        SignBlockEntity signBlockEntity,
        boolean moreUsersSign
    ) {
        SignText text = new SignText()
            .setMessage(0, Component.literal(moreUsersSign ? "[More Users]" : "[Private]"));

        if (!moreUsersSign) {
            text = text.setMessage(1, Component.literal(player.getName().getString()));
            writeSignUserUuid(signBlockEntity, player.getName().getString(), player.getUUID());
        }

        signBlockEntity.setText(text, true);
        signBlockEntity.setChanged();

        if (world instanceof ServerLevel serverWorld) {
            serverWorld.getChunkSource().blockChanged(signPos);
        }
    }

    private static boolean canPlaceWallSign(Level world, BlockPos signPos) {
        return world.isEmptyBlock(signPos);
    }

    private static void consumeSignIfNeeded(ServerPlayer player, ItemStack heldStack) {
        if (!player.isCreative()) {
            heldStack.shrink(1);
        }
    }

    private static List<AttachedSignInfo> getAttachedSigns(Level world, BlockPos lockablePos) {
        List<AttachedSignInfo> signs = new ArrayList<>();

        for (BlockPos partPos : getLockableParts(world, lockablePos)) {
            signs.addAll(getAttachedSignsForSingleBlock(world, partPos));
        }

        return signs;
    }

    private static List<AttachedSignInfo> getAttachedSignsForSingleBlock(Level world, BlockPos lockablePos) {
        List<AttachedSignInfo> signs = new ArrayList<>();

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos signPos = lockablePos.relative(direction);
            BlockState signState = world.getBlockState(signPos);

            if (signState.getBlock() instanceof WallSignBlock
                && signState.hasProperty(WallSignBlock.FACING)
                && signPos.relative(signState.getValue(WallSignBlock.FACING).getOpposite()).equals(lockablePos)) {
                signs.add(new AttachedSignInfo(signPos, readSignLines(world, signPos), readSignUserUuids(world, signPos)));
            }
        }

        BlockPos topSignPos = lockablePos.above();
        BlockState topSignState = world.getBlockState(topSignPos);
        if (topSignState.getBlock() instanceof SignBlock) {
            signs.add(new AttachedSignInfo(topSignPos, readSignLines(world, topSignPos), readSignUserUuids(world, topSignPos)));
        }

        return signs;
    }

    private static List<BlockPos> getLockableParts(Level world, BlockPos lockablePos) {
        BlockState state = world.getBlockState(lockablePos);
        if (!isChest(state)) {
            if (state.getBlock() instanceof DoorBlock && state.hasProperty(DoorBlock.HALF)) {
                BlockPos lowerPos = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                    ? lockablePos
                    : lockablePos.below();
                BlockPos upperPos = lowerPos.above();
                List<BlockPos> parts = new ArrayList<>();
                if (world.getBlockState(lowerPos).getBlock() instanceof DoorBlock) {
                    parts.add(lowerPos);
                }
                if (world.getBlockState(upperPos).getBlock() instanceof DoorBlock) {
                    parts.add(upperPos);
                }
                return parts.isEmpty() ? List.of(lockablePos) : parts;
            }

            return List.of(lockablePos);
        }

        List<BlockPos> parts = new ArrayList<>();
        parts.add(lockablePos);

        if (state.hasProperty(ChestBlock.TYPE)
            && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos otherPart = ChestBlock.getConnectedBlockPos(lockablePos, state);
            if (isChest(world.getBlockState(otherPart))) {
                parts.add(otherPart);
            }
        }

        return parts;
    }

    private static boolean isPrivateChest(List<AttachedSignInfo> signs) {
        return signs.stream().anyMatch(AttachedSignInfo::isPrivate);
    }

    private static boolean allowsRedstone(List<AttachedSignInfo> signs) {
        return signs.stream().anyMatch(AttachedSignInfo::allowsRedstone);
    }

    private static boolean hasAccessToChest(List<AttachedSignInfo> signs, ServerPlayer player) {
        return signs.stream()
            .filter(AttachedSignInfo::canGrantAccess)
            .anyMatch(sign -> sign.hasPlayer(player));
    }

    private static boolean hasAccessToChest(List<AttachedSignInfo> signs, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return hasAccessToChest(signs, serverPlayer);
        }

        return signs.stream()
            .filter(AttachedSignInfo::canGrantAccess)
            .anyMatch(sign -> sign.hasPlayerName(player.getName().getString()));
    }

    private static List<String> getAuthorizedUsers(List<AttachedSignInfo> signs) {
        List<String> users = new ArrayList<>();

        for (AttachedSignInfo sign : signs) {
            if (!sign.canGrantAccess()) {
                continue;
            }

            for (String user : sign.userNames()) {
                boolean alreadyListed = users.stream().anyMatch(existing -> existing.equalsIgnoreCase(user));
                if (!alreadyListed) {
                    users.add(user);
                }
            }
        }

        return users;
    }

    private static AttachedSignInfo getOwnerSign(List<AttachedSignInfo> signs) {
        return signs.stream()
            .filter(AttachedSignInfo::isPrivate)
            .findFirst()
            .orElse(null);
    }

    private static AttachedSignInfo getAttachedSignAt(List<AttachedSignInfo> signs, BlockPos pos) {
        return signs.stream()
            .filter(sign -> sign.pos().equals(pos))
            .findFirst()
            .orElse(null);
    }

    private static List<String> readSignLines(Level world, BlockPos signPos) {
        BlockEntity blockEntity = world.getBlockEntity(signPos);
        if (!(blockEntity instanceof SignBlockEntity signBlockEntity)) {
            return List.of();
        }

        return readSignLines(signBlockEntity.getFrontText());
    }

    private static List<String> readSignLines(SignText signText) {
        List<String> lines = new ArrayList<>();

        for (Component message : signText.getMessages(false)) {
            lines.add(message.getString().trim());
        }

        return lines;
    }

    private static Map<String, String> readSignUserUuids(Level world, BlockPos signPos) {
        BlockEntity blockEntity = world.getBlockEntity(signPos);
        if (!(blockEntity instanceof SignBlockEntity signBlockEntity)) {
            return Map.of();
        }

        return sanitizeSignUserUuids(signBlockEntity.getAttachedOrElse(SIGN_USER_UUIDS, Map.of()));
    }

    private static void hydrateSignUuidsForPlayer(
        Level world,
        List<AttachedSignInfo> signs,
        ServerPlayer player
    ) {
        for (AttachedSignInfo sign : signs) {
            if (!sign.canGrantAccess() || !sign.hasPlayerName(player.getName().getString())) {
                continue;
            }

            String key = normalizePlayerName(player.getName().getString());
            if (player.getUUID().toString().equals(sign.userUuids().get(key))) {
                continue;
            }

            BlockEntity blockEntity = world.getBlockEntity(sign.pos());
            if (blockEntity instanceof SignBlockEntity signBlockEntity) {
                writeSignUserUuid(signBlockEntity, player.getName().getString(), player.getUUID());

                if (world instanceof ServerLevel serverWorld) {
                    serverWorld.getChunkSource().blockChanged(sign.pos());
                }
            }
        }
    }

    private static void writeSignUserUuid(SignBlockEntity signBlockEntity, String playerName, UUID playerUuid) {
        Map<String, String> userUuids = new HashMap<>(signBlockEntity.getAttachedOrElse(SIGN_USER_UUIDS, Map.of()));
        userUuids.remove(LEGACY_NAME_KEY);
        userUuids.remove(LEGACY_UUID_KEY);
        userUuids.put(normalizePlayerName(playerName), playerUuid.toString());
        signBlockEntity.setAttached(SIGN_USER_UUIDS, userUuids);
        signBlockEntity.setChanged();
    }

    private static Map<String, String> sanitizeSignUserUuids(Map<String, String> userUuids) {
        if (!userUuids.containsKey(LEGACY_NAME_KEY) && !userUuids.containsKey(LEGACY_UUID_KEY)) {
            return userUuids;
        }

        Map<String, String> sanitized = new HashMap<>(userUuids);
        sanitized.remove(LEGACY_NAME_KEY);
        sanitized.remove(LEGACY_UUID_KEY);
        return sanitized;
    }

    private static String normalizePlayerName(String playerName) {
        return playerName.toLowerCase();
    }

    private static String formatBlockPos(BlockPos pos) {
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }

    private record AttachedSignInfo(BlockPos pos, List<String> lines, Map<String, String> userUuids) {
        private boolean isPrivate() {
            return !lines.isEmpty() && "[private]".equalsIgnoreCase(lines.get(0));
        }

        private boolean isMoreUsers() {
            return !lines.isEmpty() && "[more users]".equalsIgnoreCase(lines.get(0));
        }

        private boolean canGrantAccess() {
            return isPrivate() || isMoreUsers();
        }

        private boolean allowsRedstone() {
            return lines.stream().anyMatch(line -> "[redstone]".equalsIgnoreCase(line));
        }

        private boolean hasPlayerName(String playerName) {
            return lines.stream()
                .skip(1)
                .anyMatch(line -> line.equalsIgnoreCase(playerName));
        }

        private boolean hasPlayer(ServerPlayer player) {
            String playerUuid = player.getUUID().toString();
            boolean uuidMatches = userNames().stream()
                .map(SimpleServerMod::normalizePlayerName)
                .map(userUuids::get)
                .anyMatch(uuid -> playerUuid.equalsIgnoreCase(uuid));
            return uuidMatches || hasPlayerName(player.getName().getString());
        }

        private List<String> userNames() {
            return lines.stream()
                .skip(1)
                .filter(line -> !line.isBlank())
                .toList();
        }

        private String ownerName() {
            return lines.size() >= 2 ? lines.get(1) : "";
        }

        private boolean isOwner(ServerPlayer player) {
            String ownerUuid = userUuids.get(normalizePlayerName(ownerName()));
            return (ownerUuid != null && player.getUUID().toString().equalsIgnoreCase(ownerUuid))
                || ownerName().equalsIgnoreCase(player.getName().getString());
        }

        private String formattedText() {
            return String.join(" / ", lines.stream()
                .filter(line -> !line.isEmpty())
                .toList());
        }
    }

    private record PrivateSignPlacement(boolean placed, BlockPos signPos) {
        private static PrivateSignPlacement placed(BlockPos signPos) {
            return new PrivateSignPlacement(true, signPos);
        }

        private static PrivateSignPlacement failed() {
            return new PrivateSignPlacement(false, BlockPos.ZERO);
        }
    }

    private record OwnerSignKey(ResourceKey<Level> worldKey, BlockPos pos) {
    }

    private record OwnerSignWatch(
        OwnerSignKey key,
        String ownerName,
        UUID playerId,
        String playerName,
        int remainingTicks
    ) {
        private void decrement() {
            ownerSignWatches.set(ownerSignWatches.indexOf(this), new OwnerSignWatch(
                key,
                ownerName,
                playerId,
                playerName,
                remainingTicks - 1
            ));
        }
    }

    private record Messages(
        String chestLocked,
        String signLocked,
        String cannotPlaceSign,
        String ownerSignLocked,
        String chestBreakLocked,
        String signCreated
    ) {
        private static final String DEFAULT_LANGUAGE = "en";
        private static final Map<String, String> FALLBACKS = Map.of(
            "chest_locked", "This interaction is private. You are not allowed to use it.",
            "sign_locked", "This sign protects a private block. You are not allowed to edit it.",
            "cannot_place_sign", "A sign cannot be placed here without breaking a block.",
            "owner_sign_locked", "The owner name cannot be changed to prevent locking yourself out.",
            "chest_break_locked", "This block is locked. Remove the private signs before breaking it.",
            "sign_created", "Protection sign created."
        );

        private static Messages load() {
            Path configDir = configDir();
            Path messagesDir = configDir.resolve("messages");
            Path configFile = configDir.resolve("messages.yml");

            try {
                Files.createDirectories(messagesDir);
                writeDefaultFile(configFile, defaultConfig());
                writeDefaultFile(messagesDir.resolve("fr.yml"), frenchMessages());
                writeDefaultFile(messagesDir.resolve("en.yml"), englishMessages());

                String language = normalizeLanguage(readYaml(configFile).getOrDefault("language", DEFAULT_LANGUAGE));
                Map<String, String> values = new HashMap<>(FALLBACKS);
                values.putAll(readYaml(messagesDir.resolve(language + ".yml")));

                return new Messages(
                    values.get("chest_locked"),
                    values.get("sign_locked"),
                    values.get("cannot_place_sign"),
                    values.get("owner_sign_locked"),
                    values.get("chest_break_locked"),
                    values.get("sign_created")
                );
            } catch (IOException exception) {
                LOGGER.warn("Impossible de charger les messages YAML, utilisation des messages par defaut.", exception);
                return new Messages(
                    FALLBACKS.get("chest_locked"),
                    FALLBACKS.get("sign_locked"),
                    FALLBACKS.get("cannot_place_sign"),
                    FALLBACKS.get("owner_sign_locked"),
                    FALLBACKS.get("chest_break_locked"),
                    FALLBACKS.get("sign_created")
                );
            }
        }

        private static void setLanguage(String language) throws IOException {
            Path configDir = configDir();
            Files.createDirectories(configDir);
            Files.writeString(configDir.resolve("messages.yml"), """
                # Available languages: fr, en
                language: %s
                """.formatted(normalizeLanguage(language)), StandardCharsets.UTF_8);
        }

        private static Path configDir() {
            return FabricLoader.getInstance().getConfigDir().resolve(CONFIG_DIR_NAME);
        }

        private static void writeDefaultFile(Path path, String content) throws IOException {
            if (!Files.exists(path)) {
                Files.writeString(path, content, StandardCharsets.UTF_8);
            }
        }

        private static Map<String, String> readYaml(Path path) throws IOException {
            Map<String, String> values = new HashMap<>();

            for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                int separator = line.indexOf(':');
                if (separator <= 0) {
                    continue;
                }

                String key = line.substring(0, separator).trim();
                String value = line.substring(separator + 1).trim();
                values.put(key, unquote(value));
            }

            return values;
        }

        private static String unquote(String value) {
            if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
                return value.substring(1, value.length() - 1);
            }

            return value;
        }

        private static String defaultConfig() {
            return """
                # Available languages: fr, en
                language: en
                """;
        }

        private static String normalizeLanguage(String language) {
            if ("fr".equalsIgnoreCase(language)) {
                return "fr";
            }

            if ("en".equalsIgnoreCase(language)) {
                return "en";
            }

            return DEFAULT_LANGUAGE;
        }

        private static String frenchMessages() {
            return """
                chest_locked: "Cette interaction est privee. Vous n'etes pas autorise a l'utiliser."
                sign_locked: "Ce panneau protege un bloc prive. Vous n'etes pas autorise a le modifier."
                cannot_place_sign: "Impossible de poser un panneau ici sans casser un bloc."
                owner_sign_locked: "Le nom du proprietaire ne peut pas etre modifie afin d'eviter de bloquer l'acces."
                chest_break_locked: "Ce bloc est verrouille. Retirez les panneaux prives avant de le casser."
                sign_created: "Panneau de protection cree."
                """;
        }

        private static String englishMessages() {
            return """
                chest_locked: "This interaction is private. You are not allowed to use it."
                sign_locked: "This sign protects a private block. You are not allowed to edit it."
                cannot_place_sign: "A sign cannot be placed here without breaking a block."
                owner_sign_locked: "The owner name cannot be changed to prevent locking yourself out."
                chest_break_locked: "This block is locked. Remove the private signs before breaking it."
                sign_created: "Protection sign created."
                """;
        }

    }
}
