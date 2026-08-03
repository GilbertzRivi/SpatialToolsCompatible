package net.oktawia.spatialtoolscmp.items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;

import net.oktawia.spatialtoolscmp.IsModLoaded;
import net.oktawia.spatialtoolscmp.SpatialConfig;
import net.oktawia.spatialtoolscmp.compat.ae2.AE2GridLinkableHandler;
import net.oktawia.spatialtoolscmp.defs.LangDefs;
import net.oktawia.spatialtoolscmp.defs.SpatialMenuRegistrar;
import net.oktawia.spatialtoolscmp.items.helpers.ClonerBlockPlacer;
import net.oktawia.spatialtoolscmp.items.helpers.ClonerInventoryAccess;
import net.oktawia.spatialtoolscmp.items.helpers.ClonerUndoHandler;
import net.oktawia.spatialtoolscmp.logic.PiperExtension;
import net.oktawia.spatialtoolscmp.logic.PiperExtensions;
import net.oktawia.spatialtoolscmp.logic.PiperRoute;
import net.oktawia.spatialtoolscmp.logic.ReplacerExtension;
import net.oktawia.spatialtoolscmp.logic.ReplacerExtensions;
import net.oktawia.spatialtoolscmp.logic.SpatialPowerCost;
import net.oktawia.spatialtoolscmp.logic.StructureToolStackState;
import net.oktawia.spatialtoolscmp.menus.PortableSpatialPiperMenu;
import net.oktawia.spatialtoolscmp.network.NetworkHandler;
import net.oktawia.spatialtoolscmp.network.packets.ShowHudMessagePacket;

public class PortableSpatialPiper extends AbstractStructureCaptureToolItem {

    public enum FillMode {
        PATH,
        FILL
    }

    public enum PipeDirectionMode {
        OFF,
        ALONG_PATH,
        AGAINST_PATH
    }

    private static final String TAG_TARGET_BLOCK = "piperTarget";
    private static final String TAG_ROUTE = "piperRoute";
    private static final String TAG_ROUTE_DIMENSION = "piperRouteDimension";
    private static final String TAG_FILL_MODE = "piperFillMode";
    private static final String TAG_PIPE_DIRECTION_MODE = "piperPipeDirectionMode";

    public static final double POWER_COST_SCALE = 5.0D;

    private static final int HUD_DURATION = 100;

    public PortableSpatialPiper(Item.Properties properties) {
        super(
                SpatialConfig.COMMON.PORTABLE_SPATIAL_PIPER_BASE_INTERNAL_POWER_CAPACITY::get,
                4,
                4,
                properties);
    }

    @Override
    protected MenuType<?> getToolMenuType() {
        return SpatialMenuRegistrar.PORTABLE_SPATIAL_PIPER_MENU.get();
    }

    @Override
    protected boolean removeCapturedBlocks() {
        return false;
    }

    @Override
    protected Component getCaptureSuccessMessage() {
        return Component.empty();
    }

    @Override
    protected Component getStoredStructureActionNotImplementedMessage() {
        return Component.empty();
    }

    @Override
    protected double getPowerPerBlockPaste() {
        return SpatialConfig.COMMON.PORTABLE_SPATIAL_PIPER_COST.get();
    }

    @Override
    protected double getEnergyCostMultiplier() {
        return POWER_COST_SCALE
                * SpatialConfig
                        .energyCostMultiplier(SpatialConfig.COMMON.PORTABLE_SPATIAL_PIPER_ENERGY_COST_MULTIPLIER);
    }

    @Override
    public void inventoryTick(
            ItemStack stack,
            Level level,
            Entity entity,
            int slotId,
            boolean isSelected) {
        powerManager.clamp(stack);
    }

    public static ItemStack getTargetBlock(ItemStack stack) {
        CompoundTag tag = stack.getTag();

        if (tag == null || !tag.contains(TAG_TARGET_BLOCK, Tag.TAG_COMPOUND)) {
            return ItemStack.EMPTY;
        }

        return ItemStack.of(tag.getCompound(TAG_TARGET_BLOCK));
    }

    public static void setTargetBlock(ItemStack stack, ItemStack target) {
        if (target.isEmpty()) {
            CompoundTag tag = stack.getTag();

            if (tag != null) {
                tag.remove(TAG_TARGET_BLOCK);
            }

            return;
        }

        stack.getOrCreateTag().put(
                TAG_TARGET_BLOCK,
                singleTargetItem(target).save(new CompoundTag()));
    }

    public static List<BlockPos> getRoute(ItemStack stack) {
        CompoundTag tag = stack.getTag();

        if (tag == null || !tag.contains(TAG_ROUTE, Tag.TAG_INT_ARRAY)) {
            return List.of();
        }

        int[] raw = tag.getIntArray(TAG_ROUTE);
        List<BlockPos> route = new ArrayList<>(raw.length / 3);

        for (int i = 0; i + 2 < raw.length; i += 3) {
            route.add(new BlockPos(raw[i], raw[i + 1], raw[i + 2]));
        }

        return route;
    }

    public static void setRoute(ItemStack stack, List<BlockPos> route) {
        if (route.isEmpty()) {
            clearRoute(stack);
            return;
        }

        int[] raw = new int[route.size() * 3];

        for (int i = 0; i < route.size(); i++) {
            BlockPos pos = route.get(i);

            raw[i * 3] = pos.getX();
            raw[i * 3 + 1] = pos.getY();
            raw[i * 3 + 2] = pos.getZ();
        }

        stack.getOrCreateTag().putIntArray(TAG_ROUTE, raw);
    }

    public static void clearRoute(ItemStack stack) {
        CompoundTag tag = stack.getTag();

        if (tag != null) {
            tag.remove(TAG_ROUTE);
            tag.remove(TAG_ROUTE_DIMENSION);
        }
    }

    public static @Nullable ResourceKey<Level> getRouteDimension(ItemStack stack) {
        CompoundTag tag = stack.getTag();

        if (tag == null || !tag.contains(TAG_ROUTE_DIMENSION, Tag.TAG_STRING)) {
            return null;
        }

        ResourceLocation id = ResourceLocation.tryParse(tag.getString(TAG_ROUTE_DIMENSION));

        return id == null ? null : ResourceKey.create(Registries.DIMENSION, id);
    }

    public static boolean isRouteInLevel(ItemStack stack, Level level) {
        ResourceKey<Level> routeDimension = getRouteDimension(stack);

        return routeDimension == null || routeDimension.equals(level.dimension());
    }

    public static int getMaxRouteBlocks() {
        return Math.max(1, SpatialConfig.COMMON.PORTABLE_SPATIAL_PIPER_MAX_BLOCKS.get());
    }

    public static FillMode getFillMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();

        if (tag == null || !tag.contains(TAG_FILL_MODE, Tag.TAG_STRING)) {
            return FillMode.PATH;
        }

        try {
            return FillMode.valueOf(tag.getString(TAG_FILL_MODE));
        } catch (IllegalArgumentException e) {
            return FillMode.PATH;
        }
    }

    public static void cycleFillMode(ItemStack stack) {
        FillMode next = getFillMode(stack) == FillMode.PATH
                ? FillMode.FILL
                : FillMode.PATH;

        stack.getOrCreateTag().putString(TAG_FILL_MODE, next.name());
        clearRoute(stack);
    }

    public static PipeDirectionMode getPipeDirectionMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();

        if (tag == null || !tag.contains(TAG_PIPE_DIRECTION_MODE, Tag.TAG_STRING)) {
            return PipeDirectionMode.OFF;
        }

        try {
            return PipeDirectionMode.valueOf(tag.getString(TAG_PIPE_DIRECTION_MODE));
        } catch (IllegalArgumentException e) {
            return PipeDirectionMode.OFF;
        }
    }

    public static void cyclePipeDirectionMode(ItemStack stack) {
        PipeDirectionMode next = switch (getPipeDirectionMode(stack)) {
            case OFF -> PipeDirectionMode.ALONG_PATH;
            case ALONG_PATH -> PipeDirectionMode.AGAINST_PATH;
            case AGAINST_PATH -> PipeDirectionMode.OFF;
        };

        stack.getOrCreateTag().putString(TAG_PIPE_DIRECTION_MODE, next.name());
    }

    public static Set<BlockPos> expandRoute(ItemStack stack, List<BlockPos> route, int maxPositions) {
        return getFillMode(stack) == FillMode.FILL
                ? PiperRoute.expandRegion(route, maxPositions)
                : PiperRoute.expand(route, maxPositions);
    }

    public static BlockPos nextRoutePoint(ItemStack stack, List<BlockPos> route, BlockPos target) {
        if (route.isEmpty()) {
            return target.immutable();
        }

        return getFillMode(stack) == FillMode.FILL
                ? PiperRoute.snapForRegion(route, target)
                : PiperRoute.snapToAxis(route.get(route.size() - 1), target);
    }

    public static boolean acceptsMorePoints(ItemStack stack, List<BlockPos> route) {
        return getFillMode(stack) != FillMode.FILL || route.size() < 4;
    }

    public static PiperExtension.PathAction defaultPathAction(BlockState state, Block targetBlock) {
        if (state.getBlock() == targetBlock) {
            return PiperExtension.PathAction.SKIP;
        }

        if (state.isAir() || state.canBeReplaced()) {
            return PiperExtension.PathAction.BUILD;
        }

        return PiperExtension.PathAction.BLOCKED;
    }

    @Nullable
    public static BlockPos resolveRoutePos(
            Level level,
            ItemStack stack,
            Player player,
            @Nullable BlockPos clickedPos,
            @Nullable Direction clickedFace) {
        if (StructureToolStackState.isBlockInFrontSelectionMode(stack)) {
            return StructureToolStackState.getBlockInFrontSelectionPos(player).immutable();
        }

        if (clickedPos != null) {
            return placementPos(level, clickedPos, clickedFace);
        }

        BlockHitResult hit = rayTrace(level, player);

        if (hit != null) {
            return placementPos(level, hit.getBlockPos(), hit.getDirection());
        }

        return getRoute(stack).isEmpty() ? null : BlockPos.containing(rayEnd(player));
    }

    private static BlockPos placementPos(
            Level level,
            BlockPos hitPos,
            @Nullable Direction face) {
        if (face == null || level.getBlockState(hitPos).canBeReplaced()) {
            return hitPos.immutable();
        }

        return hitPos.relative(face).immutable();
    }

    private static Vec3 rayEnd(Player player) {
        double range = Math.max(1, 64);

        return player.getEyePosition().add(player.getLookAngle().normalize().scale(range));
    }

    @Nullable
    private static BlockHitResult rayTrace(Level level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = rayEnd(player);

        BlockHitResult hit = level.clip(new ClipContext(
                eye,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player));

        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide()) {
            boolean handled = hand == InteractionHand.OFF_HAND
                    || player.isShiftKeyDown()
                    || resolveRoutePos(level, stack, player, null, null) != null;

            return handled
                    ? InteractionResultHolder.success(stack)
                    : InteractionResultHolder.pass(stack);
        }

        ServerLevel serverLevel = (ServerLevel) level;
        ServerPlayer serverPlayer = (ServerPlayer) player;

        if (hand == InteractionHand.OFF_HAND) {
            if (player.isShiftKeyDown()) {
                cancelRoute(serverPlayer, stack);
            } else {
                undoLastAction(serverLevel, serverPlayer, stack);
            }

            return InteractionResultHolder.success(stack);
        }

        if (player.isShiftKeyDown()) {
            if (getRoute(stack).isEmpty()) {
                openPiperGui(serverPlayer);
            } else if (checkRouteDimension(serverLevel, serverPlayer, stack)) {
                performBuild(serverLevel, serverPlayer, stack);
            }

            return InteractionResultHolder.success(stack);
        }

        BlockPos routePos = resolveRoutePos(serverLevel, stack, player, null, null);

        if (routePos == null) {
            return InteractionResultHolder.pass(stack);
        }

        addRoutePoint(serverLevel, serverPlayer, stack, routePos);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getHand() != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }

        Player player = context.getPlayer();
        Level level = context.getLevel();

        if (player == null) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        ServerPlayer serverPlayer = (ServerPlayer) player;
        ItemStack stack = player.getMainHandItem();

        BlockPos clicked = context.getClickedPos();

        if (player.isShiftKeyDown()) {
            if (getRoute(stack).isEmpty()) {
                pickTarget(serverLevel, serverPlayer, stack, clicked);
            } else if (checkRouteDimension(serverLevel, serverPlayer, stack)) {
                performBuild(serverLevel, serverPlayer, stack);
            }

            return InteractionResult.SUCCESS;
        }

        BlockPos routePos = resolveRoutePos(
                serverLevel,
                stack,
                player,
                clicked,
                context.getClickedFace());

        if (routePos == null) {
            return InteractionResult.SUCCESS;
        }

        addRoutePoint(serverLevel, serverPlayer, stack, routePos);
        return InteractionResult.SUCCESS;
    }

    private void pickTarget(
            ServerLevel level,
            ServerPlayer player,
            ItemStack toolStack,
            BlockPos pos) {
        ItemStack picked = pickTargetItem(level, pos);

        if (picked.isEmpty()) {
            sendHud(
                    player,
                    HUD_DURATION,
                    red(Component.translatable(LangDefs.REPLACER_NO_TARGET.getTranslationKey())));
            return;
        }

        setTargetBlock(toolStack, picked);

        sendHud(
                player,
                HUD_DURATION,
                cyan(Component.translatable(LangDefs.PIPER_TARGET_LABEL.getTranslationKey())
                        .append(" ")
                        .append(picked.getHoverName())),
                cyan(Component.translatable(LangDefs.PIPER_ROUTE_HINT.getTranslationKey())));
    }

    private static ItemStack pickTargetItem(ServerLevel level, BlockPos pos) {
        BlockState clicked = level.getBlockState(pos);

        for (ReplacerExtension ext : ReplacerExtensions.get()) {
            ItemStack candidate = ext.pickTargetItem(level, pos, clicked);

            if (candidate != null && !candidate.isEmpty()) {
                return singleTargetItem(candidate);
            }
        }

        Item blockItem = clicked.getBlock().asItem();

        return blockItem != Blocks.AIR.asItem() ? new ItemStack(blockItem) : ItemStack.EMPTY;
    }

    private void addRoutePoint(
            ServerLevel level,
            ServerPlayer player,
            ItemStack toolStack,
            BlockPos pos) {
        if (!checkRouteDimension(level, player, toolStack)) {
            return;
        }

        if (getTargetBlock(toolStack).isEmpty()) {
            sendHud(
                    player,
                    HUD_DURATION,
                    red(Component.translatable(LangDefs.REPLACER_NO_TARGET.getTranslationKey())),
                    red(Component.translatable(LangDefs.PIPER_SELECT_TARGET_HINT.getTranslationKey())));
            return;
        }

        List<BlockPos> route = new ArrayList<>(getRoute(toolStack));

        if (route.isEmpty()) {
            route.add(pos);
            setRoute(toolStack, route);
            toolStack.getOrCreateTag().putString(
                    TAG_ROUTE_DIMENSION,
                    level.dimension().location().toString());

            sendHud(
                    player,
                    HUD_DURATION,
                    cyan(Component.translatable(LangDefs.PIPER_ROUTE_STARTED.getTranslationKey())),
                    cyan(Component.translatable(LangDefs.PIPER_CONFIRM_HINT.getTranslationKey())));
            return;
        }

        if (!acceptsMorePoints(toolStack, route)) {
            sendHud(
                    player,
                    HUD_DURATION,
                    red(Component.translatable(
                            LangDefs.PIPER_FILL_TOO_MANY_POINTS.getTranslationKey(),
                            4)));
            return;
        }

        boolean fill = getFillMode(toolStack) == FillMode.FILL;
        long sizeBefore = fill ? PiperRoute.regionSize(route) : PiperRoute.length(route);

        BlockPos next = nextRoutePoint(toolStack, route, pos);

        route.add(next);

        long sizeAfter = fill ? PiperRoute.regionSize(route) : PiperRoute.length(route);

        if (sizeAfter <= sizeBefore) {
            sendHud(
                    player,
                    HUD_DURATION,
                    red(Component.translatable(LangDefs.PIPER_ROUTE_POINT_SAME.getTranslationKey())));
            return;
        }

        int maxBlocks = getMaxRouteBlocks();

        if (sizeAfter > maxBlocks) {
            sendHud(
                    player,
                    HUD_DURATION,
                    red(Component.translatable(
                            LangDefs.PIPER_ROUTE_TOO_LONG.getTranslationKey(),
                            maxBlocks)));
            return;
        }

        setRoute(toolStack, route);

        sendHud(
                player,
                HUD_DURATION,
                cyan(Component.translatable(
                        LangDefs.PIPER_ROUTE_POINT_ADDED.getTranslationKey(),
                        route.size(),
                        sizeAfter)),
                cyan(Component.translatable(LangDefs.PIPER_CONFIRM_HINT.getTranslationKey())));
    }

    private void performBuild(
            ServerLevel level,
            ServerPlayer player,
            ItemStack toolStack) {
        ItemStack target = getTargetBlock(toolStack);

        if (target.isEmpty()) {
            sendHud(
                    player,
                    HUD_DURATION,
                    red(Component.translatable(LangDefs.REPLACER_NO_TARGET.getTranslationKey())));
            return;
        }

        List<BlockPos> route = getRoute(toolStack);

        if (route.isEmpty()) {
            sendHud(
                    player,
                    HUD_DURATION,
                    red(Component.translatable(LangDefs.PREVIEW_EMPTY_NO_SELECTION.getTranslationKey())));
            return;
        }

        TargetResolution resolution = resolveTarget(level, target);

        if (resolution == null) {
            sendHud(
                    player,
                    HUD_DURATION,
                    red(Component.translatable(LangDefs.PIPER_UNPLACEABLE_TARGET.getTranslationKey())));
            return;
        }

        Set<BlockPos> positions = expandRoute(toolStack, route, getMaxRouteBlocks());
        List<BlockPos> buildable = new ArrayList<>();
        int skipped = 0;

        for (BlockPos pos : positions) {
            if (resolvePathAction(level, pos, target, resolution.block()) == PiperExtension.PathAction.BUILD) {
                buildable.add(pos);
            } else {
                skipped++;
            }
        }

        if (buildable.isEmpty()) {
            sendHud(
                    player,
                    HUD_DURATION,
                    red(Component.translatable(LangDefs.PIPER_NOTHING_TO_BUILD.getTranslationKey())));
            return;
        }

        List<ItemStack> placementCost = ReplacerExtensions.placementCost(level, target);

        if (!player.isCreative()) {
            for (ItemStack cost : placementCost) {
                long available = ClonerInventoryAccess.countAvailableForPaste(
                        level,
                        player,
                        toolStack,
                        cost);

                if (available < (long) buildable.size() * cost.getCount()) {
                    sendHud(
                            player,
                            HUD_DURATION,
                            red(Component.translatable(LangDefs.REPLACER_NOT_ENOUGH_ITEMS.getTranslationKey())));
                    return;
                }
            }

            double requiredPower = getBuildPowerCost(buildable, route.get(0));

            if (requiredPower > 0.0D && !tryUsePower(player, toolStack, requiredPower)) {
                showNotEnoughPower(player, toolStack, requiredPower);
                return;
            }
        }

        Set<BlockPos> plannedPositions = new LinkedHashSet<>(buildable);

        for (PiperExtension ext : PiperExtensions.get()) {
            ext.onBeforeRouteBuilt(level, plannedPositions, target);
        }

        for (ReplacerExtension ext : ReplacerExtensions.get()) {
            ext.onBeforeReplacement(level, plannedPositions);
        }

        BlockState newState = resolution.block().defaultBlockState();
        String targetBlockId = String.valueOf(ForgeRegistries.BLOCKS.getKey(resolution.block()));

        List<ClonerUndoHandler.ClonerUndoPlacedBlock> undoBlocks = new ArrayList<>();
        Set<BlockPos> placedPositions = new LinkedHashSet<>();

        for (BlockPos pos : buildable) {
            boolean placed = resolution.provider() != null
                    ? resolution.provider().placeTarget(level, pos, target, player)
                    : ClonerBlockPlacer.placeBlockAndLoadTag(level, pos, newState, null);

            if (!placed) {
                skipped++;
                continue;
            }

            placedPositions.add(pos);
            undoBlocks.add(new ClonerUndoHandler.ClonerUndoPlacedBlock(
                    pos,
                    targetBlockId,
                    List.of()));
        }

        if (placedPositions.isEmpty()) {
            sendHud(
                    player,
                    HUD_DURATION,
                    red(Component.translatable(LangDefs.PIPER_NOTHING_TO_BUILD.getTranslationKey())));
            return;
        }

        if (!player.isCreative()) {
            for (ItemStack cost : placementCost) {
                ClonerInventoryAccess.consumeForPaste(
                        level,
                        player,
                        toolStack,
                        cost,
                        placedPositions.size() * cost.getCount());
            }
        }

        boolean connectionsHandled = false;

        if (getFillMode(toolStack) == FillMode.PATH) {
            List<BlockPos> orderedPath = new ArrayList<>(positions);

            for (PiperExtension ext : PiperExtensions.get()) {
                if (ext.onPathBuilt(level, orderedPath, placedPositions, target, toolStack)) {
                    connectionsHandled = true;
                    break;
                }
            }
        }

        if (!connectionsHandled) {
            if (resolution.provider() != null) {
                resolution.provider().onNewBlocksPlaced(level, placedPositions);
            } else {
                for (ReplacerExtension ext : ReplacerExtensions.get()) {
                    if (ext.canHandleTarget(level, resolution.block())) {
                        ext.onNewBlocksPlaced(level, placedPositions);
                        break;
                    }
                }
            }
        }

        for (ReplacerExtension ext : ReplacerExtensions.get()) {
            ext.onReplacementDone(level, placedPositions);
        }

        for (PiperExtension ext : PiperExtensions.get()) {
            ext.onRouteBuilt(level, placedPositions, target);
        }

        ClonerUndoHandler.store(toolStack, level, undoBlocks);
        clearRoute(toolStack);

        sendHud(
                player,
                HUD_DURATION,
                cyan(Component.translatable(
                        LangDefs.STRUCTURE_GADGET_PLACED_SKIPPED.getTranslationKey(),
                        placedPositions.size(),
                        skipped)),
                cyan(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO_HINT.getTranslationKey())));
    }

    private void cancelRoute(
            ServerPlayer player,
            ItemStack toolStack) {
        if (getRoute(toolStack).isEmpty()) {
            sendHud(
                    player,
                    HUD_DURATION,
                    red(Component.translatable(LangDefs.PREVIEW_EMPTY_NO_SELECTION.getTranslationKey())));
            return;
        }

        clearRoute(toolStack);

        sendHud(
                player,
                HUD_DURATION,
                cyan(Component.translatable(LangDefs.STRUCTURE_GADGET_SELECTION_CLEARED.getTranslationKey())));
    }

    private void undoLastAction(
            ServerLevel level,
            ServerPlayer player,
            ItemStack toolStack) {
        List<BlockPos> route = new ArrayList<>(getRoute(toolStack));

        if (!route.isEmpty() && isRouteInLevel(toolStack, level)) {
            route.remove(route.size() - 1);
            setRoute(toolStack, route);

            sendHud(
                    player,
                    HUD_DURATION,
                    cyan(Component.translatable(LangDefs.PIPER_ROUTE_POINT_REMOVED.getTranslationKey())),
                    cyan(Component.translatable(
                            LangDefs.PIPER_ROUTE_LABEL.getTranslationKey(),
                            route.size())));
            return;
        }

        undoLastBuild(level, player, toolStack);
    }

    private void undoLastBuild(
            ServerLevel level,
            ServerPlayer player,
            ItemStack toolStack) {
        CompoundTag stackTag = toolStack.getTag();

        if (stackTag == null || !stackTag.contains(ClonerUndoHandler.UNDO_ID_KEY, Tag.TAG_STRING)) {
            sendHud(
                    player,
                    HUD_DURATION,
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO.getTranslationKey())),
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_NOTHING_TO_UNDO.getTranslationKey())));
            return;
        }

        CompoundTag undoTag = ClonerUndoHandler.load(level, toolStack);

        if (undoTag == null || !ClonerUndoHandler.hasBlocks(undoTag)) {
            ClonerUndoHandler.clear(level, toolStack);

            sendHud(
                    player,
                    HUD_DURATION,
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO.getTranslationKey())),
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO_INVALID_CLEARED.getTranslationKey())));
            return;
        }

        if (!level.dimension().location().toString().equals(ClonerUndoHandler.getDimension(undoTag))) {
            sendHud(
                    player,
                    HUD_DURATION,
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO.getTranslationKey())),
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO_OTHER_DIMENSION.getTranslationKey())));
            return;
        }

        List<ClonerUndoHandler.ClonerUndoPlacedBlock> undoBlocks = ClonerUndoHandler.readBlocks(undoTag);

        if (undoBlocks.isEmpty()) {
            ClonerUndoHandler.clear(level, toolStack);

            sendHud(
                    player,
                    HUD_DURATION,
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO.getTranslationKey())),
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO_NOTHING_PLACED.getTranslationKey())));
            return;
        }

        if (!ClonerUndoHandler.areBlocksUnchanged(level, undoBlocks)) {
            sendHud(
                    player,
                    HUD_DURATION,
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO.getTranslationKey())),
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO_WORLD_CHANGED.getTranslationKey())));
            return;
        }

        List<ItemStack> refunds = ClonerUndoHandler.collectCurrentRefundStacks(level, undoBlocks);
        boolean shouldRefund = !player.isCreative() && !refunds.isEmpty();

        if (shouldRefund
                && !ClonerInventoryAccess.canStoreRefundStacks(level, player, toolStack, refunds)) {
            sendHud(
                    player,
                    HUD_DURATION,
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO.getTranslationKey())),
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO_NO_SPACE.getTranslationKey())));
            return;
        }

        if (!player.isCreative()) {
            List<BlockPos> undoPositions = undoBlocks.stream()
                    .map(ClonerUndoHandler.ClonerUndoPlacedBlock::pos)
                    .toList();

            double requiredPower = undoPositions.isEmpty()
                    ? 0.0D
                    : getBuildPowerCost(undoPositions, undoPositions.get(0));

            if (requiredPower > 0.0D && !tryUsePower(player, toolStack, requiredPower)) {
                showNotEnoughPower(player, toolStack, requiredPower);
                return;
            }
        }

        ClonerUndoHandler.removeBlocks(level, undoBlocks);

        ClonerInventoryAccess.ClonerRefundResult refundResult = ClonerInventoryAccess.ClonerRefundResult.success(false);

        if (shouldRefund) {
            refundResult = ClonerInventoryAccess.refundStacksToAeThenInventory(
                    level,
                    player,
                    toolStack,
                    refunds);
        }

        ClonerUndoHandler.clear(level, toolStack);

        sendHud(
                player,
                HUD_DURATION,
                cyan(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO.getTranslationKey())),
                cyan(Component.translatable(LangDefs.STRUCTURE_GADGET_COPY_PASTE_UNDONE.getTranslationKey())),
                shouldRefund
                        ? cyan(Component.translatable(
                                refundResult.insertedIntoMe()
                                        ? LangDefs.STRUCTURE_GADGET_ITEMS_REFUNDED_TO_ME.getTranslationKey()
                                        : LangDefs.STRUCTURE_GADGET_ITEMS_REFUNDED.getTranslationKey()))
                        : null);
    }

    private boolean checkRouteDimension(
            ServerLevel level,
            ServerPlayer player,
            ItemStack toolStack) {
        if (getRoute(toolStack).isEmpty() || isRouteInLevel(toolStack, level)) {
            return true;
        }

        ResourceKey<Level> routeDimension = getRouteDimension(toolStack);

        sendHud(
                player,
                HUD_DURATION,
                red(Component.translatable(
                        LangDefs.PIPER_ROUTE_OTHER_DIMENSION.getTranslationKey(),
                        routeDimension == null ? "" : routeDimension.location().toString())),
                red(Component.translatable(LangDefs.PIPER_CANCEL_HINT.getTranslationKey())));

        return false;
    }

    private double getBuildPowerCost(Collection<BlockPos> positions, BlockPos origin) {
        return SpatialPowerCost.cost(
                positions,
                origin,
                getPowerPerBlockPaste() * getEnergyCostMultiplier());
    }

    private static PiperExtension.PathAction resolvePathAction(
            ServerLevel level,
            BlockPos pos,
            ItemStack target,
            Block targetBlock) {
        BlockState state = level.getBlockState(pos);

        for (PiperExtension ext : PiperExtensions.get()) {
            PiperExtension.PathAction action = ext.resolvePathAction(level, pos, state, target);

            if (action != null) {
                return action;
            }
        }

        return defaultPathAction(state, targetBlock);
    }

    private record TargetResolution(Block block, @Nullable ReplacerExtension provider) {
    }

    @Nullable
    private static TargetResolution resolveTarget(ServerLevel level, ItemStack target) {
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(target.getItem());
        Block targetBlock = itemId != null ? ForgeRegistries.BLOCKS.getValue(itemId) : null;

        if (targetBlock != null && targetBlock != Blocks.AIR) {
            for (ReplacerExtension ext : ReplacerExtensions.get()) {
                if (ext.isUnplaceableTarget(level, targetBlock, target)) {
                    targetBlock = null;
                    break;
                }
            }
        }

        if (targetBlock != null && targetBlock != Blocks.AIR) {
            return new TargetResolution(targetBlock, null);
        }

        for (ReplacerExtension ext : ReplacerExtensions.get()) {
            Block resolved = ext.resolveTargetBlock(level, target);

            if (resolved != null && resolved != Blocks.AIR) {
                return new TargetResolution(resolved, ext);
            }
        }

        return null;
    }

    private static ItemStack singleTargetItem(ItemStack target) {
        ItemStack single = target.copy();
        single.setCount(1);

        return single;
    }

    private void openPiperGui(ServerPlayer player) {
        ItemStack toolStack = player.getMainHandItem();

        NetworkHooks.openScreen(player, new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return toolStack.getHoverName();
            }

            @Override
            public @Nullable AbstractContainerMenu createMenu(
                    int id,
                    Inventory inventory,
                    Player p) {
                return new PortableSpatialPiperMenu(id, inventory);
            }
        });
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        if (Screen.hasShiftDown()) {
            ItemStack target = getTargetBlock(stack);

            if (!target.isEmpty()) {
                tooltip.add(Component.translatable(LangDefs.PIPER_TARGET_LABEL.getTranslationKey())
                        .append(" ")
                        .append(target.getHoverName())
                        .withStyle(ChatFormatting.AQUA));
            }

            List<BlockPos> route = getRoute(stack);

            tooltip.add(Component.translatable(
                    LangDefs.PIPER_ROUTE_LABEL.getTranslationKey(),
                    route.size())
                    .withStyle(ChatFormatting.GRAY));

            tooltip.add(Component.translatable(
                    LangDefs.STRUCTURE_SIZE.getTranslationKey(),
                    getFillMode(stack) == FillMode.FILL
                            ? PiperRoute.regionSize(route)
                            : PiperRoute.length(route))
                    .withStyle(ChatFormatting.GRAY));

            if (!route.isEmpty() && level != null && !isRouteInLevel(stack, level)) {
                ResourceKey<Level> routeDimension = getRouteDimension(stack);

                tooltip.add(Component.translatable(
                        LangDefs.PIPER_ROUTE_OTHER_DIMENSION.getTranslationKey(),
                        routeDimension == null ? "" : routeDimension.location().toString())
                        .withStyle(ChatFormatting.RED));
            }

            if (IsModLoaded.AE2) {
                try {
                    GlobalPos ae2Pos = AE2GridLinkableHandler.getLinkedPos(stack);

                    if (ae2Pos != null) {
                        tooltip.add(Component.translatable(
                                LangDefs.PORTABLE_SPATIAL_CLONER_LINKED_TO_AE2.getTranslationKey(),
                                ae2Pos.pos().getX()
                                        + " "
                                        + ae2Pos.pos().getY()
                                        + " "
                                        + ae2Pos.pos().getZ())
                                .withStyle(ChatFormatting.AQUA));

                        tooltip.add(Component.translatable(
                                LangDefs.PORTABLE_SPATIAL_CLONER_LINK_DIMENSION.getTranslationKey(),
                                ae2Pos.dimension().location().toString())
                                .withStyle(ChatFormatting.GRAY));
                    }
                } catch (Throwable ignored) {
                }
            }
        } else {
            tooltip.add(Component.translatable(LangDefs.PIPER_SELECT_TARGET_HINT.getTranslationKey())
                    .withStyle(ChatFormatting.GRAY));

            tooltip.add(Component.translatable(LangDefs.PIPER_ROUTE_HINT.getTranslationKey())
                    .withStyle(ChatFormatting.GRAY));

            tooltip.add(Component.translatable(LangDefs.PIPER_CONFIRM_HINT.getTranslationKey())
                    .withStyle(ChatFormatting.GRAY));

            tooltip.add(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO_HINT.getTranslationKey())
                    .withStyle(ChatFormatting.GRAY));

            tooltip.add(Component.translatable(LangDefs.PIPER_CANCEL_HINT.getTranslationKey())
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static void sendHud(
            ServerPlayer player,
            int duration,
            ShowHudMessagePacket.Line... lines) {
        List<ShowHudMessagePacket.Line> filtered = new ArrayList<>();

        for (ShowHudMessagePacket.Line line : lines) {
            if (line != null) {
                filtered.add(line);
            }
        }

        NetworkHandler.sendToPlayer(
                player,
                new ShowHudMessagePacket(duration, filtered));
    }
}
