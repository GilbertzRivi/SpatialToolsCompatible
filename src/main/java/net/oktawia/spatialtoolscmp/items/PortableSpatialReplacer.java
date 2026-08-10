package net.oktawia.spatialtoolscmp.items;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiPredicate;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;

import net.oktawia.spatialtoolscmp.IsModLoaded;
import net.oktawia.spatialtoolscmp.SpatialConfig;
import net.oktawia.spatialtoolscmp.compat.ae2.AE2GridLinkableHandler;
import net.oktawia.spatialtoolscmp.defs.LangDefs;
import net.oktawia.spatialtoolscmp.defs.SpatialMenuRegistrar;
import net.oktawia.spatialtoolscmp.items.helpers.ClonerInventoryAccess;
import net.oktawia.spatialtoolscmp.items.helpers.ClonerUndoHandler;
import net.oktawia.spatialtoolscmp.logic.ReplacerBlacklist;
import net.oktawia.spatialtoolscmp.logic.ReplacerContext;
import net.oktawia.spatialtoolscmp.logic.ReplacerContext.ConnectivityMode;
import net.oktawia.spatialtoolscmp.logic.ReplacerExtension;
import net.oktawia.spatialtoolscmp.logic.ReplacerExtensions;
import net.oktawia.spatialtoolscmp.logic.SpatialPowerCost;
import net.oktawia.spatialtoolscmp.logic.StructureCloneExtension;
import net.oktawia.spatialtoolscmp.logic.StructureToolExtensions;
import net.oktawia.spatialtoolscmp.menus.PortableSpatialReplacerMenu;
import net.oktawia.spatialtoolscmp.network.NetworkHandler;
import net.oktawia.spatialtoolscmp.network.packets.ShowHudMessagePacket;

public class PortableSpatialReplacer extends AbstractStructureCaptureToolItem {

    private static final String TAG_TARGET_BLOCK = "replacerTarget";
    private static final String TAG_RADIUS = "replacerRadius";
    private static final String TAG_CONNECTIVITY = "replacerConnectivity";
    private static final String TAG_STRICT_BLOCKSTATE = "replacerStrictBlockstate";

    public static final int DEFAULT_RADIUS = 16;
    public static final int MIN_RADIUS = 1;
    public static final int MAX_RADIUS = 128;

    public static final double POWER_COST_SCALE = 5.0D;

    private static final int HUD_DURATION = 100;

    public PortableSpatialReplacer(Item.Properties properties) {
        super(
                SpatialConfig.COMMON.PORTABLE_SPATIAL_REPLACER_BASE_INTERNAL_POWER_CAPACITY::get,
                4,
                4,
                properties);
    }

    @Override
    protected MenuType<?> getToolMenuType() {
        return SpatialMenuRegistrar.PORTABLE_SPATIAL_REPLACER_MENU.get();
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
        return SpatialConfig.COMMON.PORTABLE_SPATIAL_REPLACER_COST.get();
    }

    @Override
    protected double getEnergyCostMultiplier() {
        return POWER_COST_SCALE
                * SpatialConfig
                        .energyCostMultiplier(SpatialConfig.COMMON.PORTABLE_SPATIAL_REPLACER_ENERGY_COST_MULTIPLIER);
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

        ItemStack single = target.copy();
        single.setCount(1);

        stack.getOrCreateTag().put(
                TAG_TARGET_BLOCK,
                single.save(new CompoundTag()));
    }

    public static int getRadius(ItemStack stack) {
        CompoundTag tag = stack.getTag();

        if (tag == null || !tag.contains(TAG_RADIUS, Tag.TAG_INT)) {
            return DEFAULT_RADIUS;
        }

        return Math.max(
                MIN_RADIUS,
                Math.min(MAX_RADIUS, tag.getInt(TAG_RADIUS)));
    }

    public static void setRadius(ItemStack stack, int radius) {
        stack.getOrCreateTag().putInt(
                TAG_RADIUS,
                Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius)));
    }

    public static ConnectivityMode getConnectivityMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();

        if (tag == null || !tag.contains(TAG_CONNECTIVITY, Tag.TAG_STRING)) {
            return ConnectivityMode.DIRECT;
        }

        try {
            return ConnectivityMode.valueOf(tag.getString(TAG_CONNECTIVITY));
        } catch (IllegalArgumentException e) {
            return ConnectivityMode.DIRECT;
        }
    }

    public static void setConnectivityMode(ItemStack stack, ConnectivityMode mode) {
        stack.getOrCreateTag().putString(TAG_CONNECTIVITY, mode.name());
    }

    public static void cycleConnectivityMode(ItemStack stack) {
        ConnectivityMode current = getConnectivityMode(stack);

        setConnectivityMode(
                stack,
                current == ConnectivityMode.DIRECT
                        ? ConnectivityMode.DIAGONAL
                        : ConnectivityMode.DIRECT);
    }

    public static boolean isSameBlockstate(ItemStack stack) {
        CompoundTag tag = stack.getTag();

        return tag != null && tag.getBoolean(TAG_STRICT_BLOCKSTATE);
    }

    public static void setSameBlockstate(ItemStack stack, boolean value) {
        stack.getOrCreateTag().putBoolean(TAG_STRICT_BLOCKSTATE, value);
    }

    public static void toggleSameBlockstate(ItemStack stack) {
        setSameBlockstate(stack, !isSameBlockstate(stack));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (hand == InteractionHand.OFF_HAND) {
            if (!level.isClientSide()) {
                performUndo((ServerLevel) level, (ServerPlayer) player, stack);
            }

            return InteractionResultHolder.success(stack);
        }

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                openReplacerGui((ServerPlayer) player);
            }

            return InteractionResultHolder.success(stack);
        }

        return InteractionResultHolder.pass(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getHand() != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }

        Player player = context.getPlayer();

        if (player == null) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        ItemStack stack = player.getMainHandItem();

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                ItemStack picked = pickTargetItem((ServerLevel) level, pos);

                if (!picked.isEmpty()) {
                    setTargetBlock(stack, picked);

                    sendHud(
                            (ServerPlayer) player,
                            HUD_DURATION,
                            cyan(Component.translatable(LangDefs.REPLACER_TARGET_LABEL.getTranslationKey())
                                    .append(" ")
                                    .append(picked.getHoverName())));
                }
            }

            return InteractionResult.SUCCESS;
        }

        if (!level.isClientSide()) {
            performReplacement(
                    (ServerLevel) level,
                    (ServerPlayer) player,
                    stack,
                    pos);
        }

        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private static ItemStack singleTargetItem(ItemStack target) {
        ItemStack single = target.copy();
        single.setCount(1);

        return single;
    }

    private static boolean needsReplacement(ServerLevel level, BlockPos pos, ItemStack target) {
        for (ReplacerExtension ext : ReplacerExtensions.get()) {
            if (ext.needsReplacement(level, pos, target)) {
                return true;
            }
        }

        return false;
    }

    private static ItemStack pickTargetItem(ServerLevel level, BlockPos pos) {
        BlockState clicked = level.getBlockState(pos);

        for (ReplacerExtension ext : ReplacerExtensions.get()) {
            ItemStack candidate = ext.pickTargetItem(level, pos, clicked);

            if (candidate != null && !candidate.isEmpty()) {
                return candidate;
            }
        }

        Item blockItem = clicked.getBlock().asItem();

        return blockItem != Blocks.AIR.asItem() ? new ItemStack(blockItem) : ItemStack.EMPTY;
    }

    private void performReplacement(
            ServerLevel level,
            ServerPlayer player,
            ItemStack toolStack,
            BlockPos startPos) {
        ItemStack target = getTargetBlock(toolStack);

        if (target.isEmpty()) {
            sendHud(
                    player,
                    HUD_DURATION,
                    red(Component.translatable(LangDefs.REPLACER_NO_TARGET.getTranslationKey())));
            return;
        }

        Block targetBlock = ForgeRegistries.ITEMS.getKey(target.getItem()) != null
                ? ForgeRegistries.BLOCKS.getValue(ForgeRegistries.ITEMS.getKey(target.getItem()))
                : null;

        ReplacerExtension targetProvider = null;

        if (targetBlock != null && targetBlock != Blocks.AIR) {
            for (ReplacerExtension ext : ReplacerExtensions.get()) {
                if (ext.isUnplaceableTarget(level, targetBlock, target)) {
                    targetBlock = null;
                    break;
                }
            }
        }

        if (targetBlock == null || targetBlock == Blocks.AIR) {
            for (ReplacerExtension ext : ReplacerExtensions.get()) {
                Block resolved = ext.resolveTargetBlock(level, target);

                if (resolved != null && resolved != Blocks.AIR) {
                    targetBlock = resolved;
                    targetProvider = ext;
                    break;
                }
            }
        }

        if (targetBlock == null || targetBlock == Blocks.AIR) {
            sendHud(
                    player,
                    HUD_DURATION,
                    red(Component.translatable(LangDefs.REPLACER_NO_TARGET.getTranslationKey())));
            return;
        }

        BlockState sourceState = level.getBlockState(startPos);

        if (sourceState.isAir()) {
            return;
        }

        if (ReplacerBlacklist.isProtected(level, startPos, sourceState)) {
            sendHud(
                    player,
                    HUD_DURATION,
                    red(Component.translatable(LangDefs.REPLACER_BLACKLISTED.getTranslationKey())));
            return;
        }

        if (sourceState.getBlock() == targetBlock
                && !needsReplacement(level, startPos, target)) {
            sendHud(
                    player,
                    HUD_DURATION,
                    red(Component.translatable(LangDefs.REPLACER_NOTHING_TO_REPLACE.getTranslationKey())));
            return;
        }

        int radius = getRadius(toolStack);
        int hardCap = SpatialConfig.COMMON.PORTABLE_SPATIAL_REPLACER_MAX_BLOCKS.get();
        ConnectivityMode mode = getConnectivityMode(toolStack);
        boolean strict = isSameBlockstate(toolStack);

        ReplacerContext ctx = new ReplacerContext(
                radius,
                hardCap,
                mode,
                strict);

        Set<BlockPos> positions = null;
        ReplacerExtension usedExtension = null;

        for (ReplacerExtension ext : ReplacerExtensions.get()) {
            if (ext.canHandleSource(level, startPos, sourceState)) {
                positions = ext.findReplacementTargets(
                        level,
                        startPos,
                        sourceState,
                        ctx);

                usedExtension = ext;
                break;
            }
        }

        if (positions == null) {
            positions = floodFill(level, startPos, sourceState, ctx);
        }

        positions = filterProtected(level, positions);

        if (positions.isEmpty()) {
            sendHud(
                    player,
                    HUD_DURATION,
                    red(Component.translatable(LangDefs.REPLACER_NOTHING_TO_REPLACE.getTranslationKey())));
            return;
        }

        for (BlockPos pos : positions) {
            BlockState s = level.getBlockState(pos);
            BlockEntity be = level.getBlockEntity(pos);

            for (StructureCloneExtension ext : StructureToolExtensions.clonerExtensions()) {
                if (ext.hasNonEmptyStorage(level, pos, s, be)) {
                    sendHud(
                            player,
                            HUD_DURATION,
                            red(Component.translatable(LangDefs.REPLACER_NON_EMPTY_STORAGE.getTranslationKey())));
                    return;
                }
            }
        }

        Set<BlockPos> matchingPositions = collectMatchingPositions(
                level,
                positions,
                sourceState,
                strict);

        int replaceCount = matchingPositions.size();

        if (replaceCount <= 0) {
            sendHud(
                    player,
                    HUD_DURATION,
                    red(Component.translatable(LangDefs.REPLACER_NOTHING_TO_REPLACE.getTranslationKey())));
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

                if (available < (long) replaceCount * cost.getCount()) {
                    sendHud(
                            player,
                            HUD_DURATION,
                            red(Component.translatable(LangDefs.REPLACER_NOT_ENOUGH_ITEMS.getTranslationKey())));
                    return;
                }
            }
        }

        Map<BlockPos, List<ItemStack>> preservedSourceRefunds = new LinkedHashMap<>();

        for (BlockPos pos : positions) {
            BlockState s = level.getBlockState(pos);

            if (strict ? !s.equals(sourceState) : s.getBlock() != sourceState.getBlock()) {
                continue;
            }

            BlockEntity be = level.getBlockEntity(pos);

            for (ReplacerExtension ext : ReplacerExtensions.get()) {
                List<ItemStack> preserved = ext.collectPreservedSourceRefunds(level, pos, s, be, targetBlock);

                if (preserved != null) {
                    preservedSourceRefunds.put(pos.immutable(), preserved);
                    break;
                }
            }
        }

        Map<BlockPos, List<ItemStack>> preCollectedRefunds = new LinkedHashMap<>();

        if (!player.isCreative()) {
            for (BlockPos pos : positions) {
                BlockState s = level.getBlockState(pos);

                if (strict ? !s.equals(sourceState) : s.getBlock() != sourceState.getBlock()) {
                    continue;
                }

                BlockEntity be = level.getBlockEntity(pos);
                List<ItemStack> posRefunds = new ArrayList<>();
                boolean handled = false;

                ItemStack swapped = targetProvider != null
                        ? targetProvider.getInPlaceSwapItem(level, pos, target)
                        : null;

                if (swapped != null && !swapped.isEmpty()) {
                    posRefunds.add(swapped);
                    preCollectedRefunds.put(pos.immutable(), posRefunds);
                    continue;
                }

                List<ItemStack> preserved = preservedSourceRefunds.get(pos.immutable());

                if (preserved != null) {
                    for (ItemStack stack : preserved) {
                        posRefunds.add(stack.copy());
                    }

                    preCollectedRefunds.put(pos.immutable(), posRefunds);
                    continue;
                }

                for (StructureCloneExtension ext : StructureToolExtensions.clonerExtensions()) {
                    if (ext.collectUndoRefunds(level, pos, s, be, posRefunds)) {
                        handled = true;
                        break;
                    }
                }

                if (!handled) {
                    Item item = s.getBlock().asItem();

                    if (item != Items.AIR) {
                        posRefunds.add(new ItemStack(item));
                    }
                }

                preCollectedRefunds.put(pos.immutable(), posRefunds);
            }

            List<ItemStack> allSourceItems = new ArrayList<>();
            preCollectedRefunds.values().forEach(allSourceItems::addAll);

            List<ItemStack> aggregated = ClonerUndoHandler.aggregateRefundStacks(allSourceItems);

            if (!aggregated.isEmpty()
                    && !ClonerInventoryAccess.canStoreRefundStacks(level, player, toolStack, aggregated)) {
                sendHud(
                        player,
                        HUD_DURATION,
                        red(Component.translatable(LangDefs.REPLACER_NO_SPACE_FOR_SOURCE.getTranslationKey())));
                return;
            }
        }

        CompoundTag originalStateNbt = NbtUtils.writeBlockState(sourceState);

        Map<BlockPos, CompoundTag> perPosStateTags = new LinkedHashMap<>();
        Map<BlockPos, CompoundTag> perPosBeTags = new LinkedHashMap<>();

        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);

            if (strict ? !state.equals(sourceState) : state.getBlock() != sourceState.getBlock()) {
                continue;
            }

            BlockPos immutablePos = pos.immutable();

            perPosStateTags.put(
                    immutablePos,
                    NbtUtils.writeBlockState(state));

            BlockEntity be = level.getBlockEntity(pos);

            if (be != null) {
                try {
                    perPosBeTags.put(
                            immutablePos,
                            be.saveWithoutMetadata());
                } catch (Throwable ignored) {
                }
            }
        }

        Map<BlockPos, CompoundTag> savedStates = new LinkedHashMap<>();

        if (usedExtension != null) {
            for (BlockPos pos : positions) {
                CompoundTag saved = usedExtension.capturePreReplacementState(level, pos);

                if (saved != null) {
                    savedStates.put(pos.immutable(), saved);
                }
            }
        }

        if (!player.isCreative()) {
            double requiredPower = getReplacementPowerCost(matchingPositions, startPos);

            if (requiredPower > 0.0D && !tryUsePower(player, toolStack, requiredPower)) {
                showNotEnoughPower(player, toolStack, requiredPower);
                return;
            }
        }

        BlockState newState = targetBlock.defaultBlockState();
        List<ClonerUndoHandler.ClonerUndoPlacedBlock> undoBlocks = new ArrayList<>();

        for (ReplacerExtension ext : ReplacerExtensions.get()) {
            ext.onBeforeReplacement(level, positions);
        }

        int replaced = 0;
        List<ItemStack> replacedSourceItems = new ArrayList<>();

        for (BlockPos pos : positions) {
            BlockState existing = level.getBlockState(pos);

            if (strict ? !existing.equals(sourceState) : existing.getBlock() != sourceState.getBlock()) {
                continue;
            }

            List<ItemStack> refunds = player.isCreative()
                    ? new ArrayList<>()
                    : preCollectedRefunds.getOrDefault(pos, new ArrayList<>());

            boolean inPlace = targetProvider != null
                    && targetProvider.getInPlaceSwapItem(level, pos, target) != null;

            if (targetProvider != null) {
                if (!targetProvider.placeTarget(level, pos, target, player)) {
                    continue;
                }
            } else {
                level.setBlock(pos, newState, Block.UPDATE_ALL);
            }

            replaced++;
            replacedSourceItems.addAll(refunds);

            List<ItemStack> undoRefunds;

            if (preservedSourceRefunds.containsKey(pos.immutable())) {
                undoRefunds = placementCost.stream().map(ItemStack::copy).toList();
            } else if (inPlace) {
                undoRefunds = List.of(singleTargetItem(target));
            } else {
                undoRefunds = List.of();
            }

            undoBlocks.add(new ClonerUndoHandler.ClonerUndoPlacedBlock(
                    pos.immutable(),
                    ForgeRegistries.BLOCKS.getKey(targetBlock).toString(),
                    refunds,
                    undoRefunds));
        }

        if (!player.isCreative() && replaced > 0) {
            for (ItemStack cost : placementCost) {
                ClonerInventoryAccess.consumeForPaste(
                        level,
                        player,
                        toolStack,
                        cost,
                        replaced * cost.getCount());
            }

            if (!replacedSourceItems.isEmpty()) {
                ClonerInventoryAccess.refundStacksToAeThenInventory(
                        level,
                        player,
                        toolStack,
                        replacedSourceItems);
            }
        }

        if (usedExtension != null) {
            for (BlockPos pos : positions) {
                usedExtension.onBlockReplaced(
                        level,
                        pos,
                        savedStates.get(pos.immutable()));
            }
        } else {
            for (ReplacerExtension ext : ReplacerExtensions.get()) {
                if (ext.canHandleTarget(level, targetBlock)) {
                    ext.onNewBlocksPlaced(level, positions);
                    break;
                }
            }
        }

        if (replaced > 0) {
            for (ReplacerExtension ext : ReplacerExtensions.get()) {
                ext.onReplacementDone(level, positions);
            }
        }

        if (replaced > 0) {
            ClonerUndoHandler.storeForReplacer(
                    toolStack,
                    level,
                    undoBlocks,
                    originalStateNbt,
                    perPosStateTags,
                    perPosBeTags);

            sendHud(
                    player,
                    HUD_DURATION,
                    cyan(Component.translatable(LangDefs.REPLACER_REPLACED.getTranslationKey(), replaced)),
                    cyan(Component.translatable(LangDefs.REPLACER_UNDO_HINT.getTranslationKey())));
        } else {
            sendHud(
                    player,
                    HUD_DURATION,
                    red(Component.translatable(LangDefs.REPLACER_NOTHING_TO_REPLACE.getTranslationKey())));
        }
    }

    private double getReplacementPowerCost(Collection<BlockPos> positions, BlockPos origin) {
        return SpatialPowerCost.cost(
                positions,
                origin,
                getPowerPerBlockPaste() * getEnergyCostMultiplier());
    }

    private Set<BlockPos> collectMatchingPositions(
            ServerLevel level,
            Set<BlockPos> positions,
            BlockState sourceState,
            boolean strict) {
        Set<BlockPos> matching = new LinkedHashSet<>();

        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);

            if (strict ? state.equals(sourceState) : state.getBlock() == sourceState.getBlock()) {
                matching.add(pos.immutable());
            }
        }

        return matching;
    }

    public static Set<BlockPos> filterProtected(BlockGetter level, Set<BlockPos> positions) {
        Set<BlockPos> allowed = new LinkedHashSet<>(positions.size());

        for (BlockPos pos : positions) {
            if (!ReplacerBlacklist.isProtected(level, pos, level.getBlockState(pos))) {
                allowed.add(pos);
            }
        }

        return allowed;
    }

    public static Set<BlockPos> floodFill(
            BlockGetter level,
            BlockPos startPos,
            BlockState sourceState,
            ReplacerContext ctx) {
        boolean strict = ctx.strictBlockstate();

        return floodFill(level, startPos, ctx, (checkedLevel, pos) -> {
            BlockState state = checkedLevel.getBlockState(pos);

            return strict
                    ? state.equals(sourceState)
                    : state.getBlock() == sourceState.getBlock();
        });
    }

    public static Set<BlockPos> floodFill(
            BlockGetter level,
            BlockPos startPos,
            ReplacerContext ctx,
            BiPredicate<BlockGetter, BlockPos> matcher) {
        int hardCap = ctx.hardCapMax();
        int radius = ctx.radius();
        boolean diagonal = ctx.connectivityMode() == ConnectivityMode.DIAGONAL;

        Set<BlockPos> visited = new LinkedHashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();

        queue.add(startPos.immutable());
        visited.add(startPos.immutable());

        while (!queue.isEmpty() && visited.size() < hardCap) {
            BlockPos current = queue.poll();

            for (BlockPos neighbor : getNeighbors(current, diagonal)) {
                if (visited.contains(neighbor)) {
                    continue;
                }

                if (diagonal) {
                    if (Math.abs(neighbor.getX() - startPos.getX()) > radius
                            || Math.abs(neighbor.getY() - startPos.getY()) > radius
                            || Math.abs(neighbor.getZ() - startPos.getZ()) > radius) {
                        continue;
                    }
                } else {
                    double dist = Math.sqrt(
                            Math.pow(neighbor.getX() - startPos.getX(), 2)
                                    + Math.pow(neighbor.getY() - startPos.getY(), 2)
                                    + Math.pow(neighbor.getZ() - startPos.getZ(), 2));

                    if (dist > radius) {
                        continue;
                    }
                }

                if (matcher.test(level, neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        return visited;
    }

    private static List<BlockPos> getNeighbors(BlockPos pos, boolean diagonal) {
        List<BlockPos> neighbors = new ArrayList<>();

        if (diagonal) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }

                        neighbors.add(pos.offset(dx, dy, dz).immutable());
                    }
                }
            }
        } else {
            neighbors.add(pos.north().immutable());
            neighbors.add(pos.south().immutable());
            neighbors.add(pos.east().immutable());
            neighbors.add(pos.west().immutable());
            neighbors.add(pos.above().immutable());
            neighbors.add(pos.below().immutable());
        }

        return neighbors;
    }

    private void performUndo(
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

        String undoDimension = ClonerUndoHandler.getDimension(undoTag);

        if (!level.dimension().location().toString().equals(undoDimension)) {
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

        List<ItemStack> sourceItems = ClonerUndoHandler.collectRefundStacks(undoBlocks);
        boolean hasSourceItems = !sourceItems.isEmpty();

        if (!player.isCreative() && hasSourceItems) {
            for (ItemStack needed : sourceItems) {
                long available = ClonerInventoryAccess.countAvailableForPaste(
                        level,
                        player,
                        toolStack,
                        needed);

                if (available < needed.getCount()) {
                    sendHud(
                            player,
                            HUD_DURATION,
                            red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO.getTranslationKey())),
                            red(Component.translatable(LangDefs.REPLACER_UNDO_NEED_SOURCE_ITEMS.getTranslationKey())));
                    return;
                }
            }
        }

        List<ItemStack> targetRefunds = ClonerUndoHandler.collectCurrentRefundStacks(level, undoBlocks);

        boolean shouldRefundTarget = !player.isCreative() && !targetRefunds.isEmpty();

        if (shouldRefundTarget
                && !ClonerInventoryAccess.canStoreRefundStacks(level, player, toolStack, targetRefunds)) {
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
                    : getReplacementPowerCost(undoPositions, undoPositions.get(0));

            if (requiredPower > 0.0D && !tryUsePower(player, toolStack, requiredPower)) {
                showNotEnoughPower(player, toolStack, requiredPower);
                return;
            }
        }

        CompoundTag originalStateNbt = ClonerUndoHandler.getOriginalStateNbt(undoTag);

        if (originalStateNbt != null) {
            Map<BlockPos, CompoundTag> perPosStateTags = ClonerUndoHandler.getPerPosStateTags(undoTag);

            Map<BlockPos, CompoundTag> perPosBeTags = ClonerUndoHandler.getPerPosBeTags(undoTag);

            if (!perPosStateTags.isEmpty() || !perPosBeTags.isEmpty()) {
                ClonerUndoHandler.restoreBlocksWithPerPosStatesAndTags(
                        level,
                        undoBlocks,
                        originalStateNbt,
                        perPosStateTags,
                        perPosBeTags);

                for (ClonerUndoHandler.ClonerUndoPlacedBlock undoBlock : undoBlocks) {
                    BlockPos pos = undoBlock.pos();
                    BlockEntity be = level.getBlockEntity(pos);

                    for (StructureCloneExtension ext : StructureToolExtensions.clonerExtensions()) {
                        ext.onBlockRestored(
                                level,
                                pos,
                                be,
                                perPosBeTags.get(pos));
                    }
                }
            } else {
                CompoundTag originalBeTag = ClonerUndoHandler.getOriginalBeTag(undoTag);

                ClonerUndoHandler.restoreBlocks(
                        level,
                        undoBlocks,
                        originalStateNbt,
                        originalBeTag);
            }
        } else {
            ClonerUndoHandler.removeBlocks(level, undoBlocks);
        }

        if (!player.isCreative() && hasSourceItems) {
            for (ItemStack needed : sourceItems) {
                ClonerInventoryAccess.consumeForPaste(
                        level,
                        player,
                        toolStack,
                        needed,
                        needed.getCount());
            }
        }

        ClonerInventoryAccess.ClonerRefundResult refundResult = ClonerInventoryAccess.ClonerRefundResult.success(false);

        if (shouldRefundTarget) {
            refundResult = ClonerInventoryAccess.refundStacksToAeThenInventory(
                    level,
                    player,
                    toolStack,
                    targetRefunds);
        }

        ClonerUndoHandler.clear(level, toolStack);

        sendHud(
                player,
                HUD_DURATION,
                cyan(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO.getTranslationKey())),
                cyan(Component.translatable(LangDefs.STRUCTURE_GADGET_COPY_PASTE_UNDONE.getTranslationKey())),
                shouldRefundTarget
                        ? cyan(Component.translatable(
                                refundResult.insertedIntoMe()
                                        ? LangDefs.STRUCTURE_GADGET_ITEMS_REFUNDED_TO_ME.getTranslationKey()
                                        : LangDefs.STRUCTURE_GADGET_ITEMS_REFUNDED.getTranslationKey()))
                        : null);
    }

    private void openReplacerGui(ServerPlayer player) {
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
                return new PortableSpatialReplacerMenu(id, inventory);
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
                tooltip.add(Component.translatable(LangDefs.REPLACER_TARGET_LABEL.getTranslationKey())
                        .append(" ")
                        .append(target.getHoverName())
                        .withStyle(ChatFormatting.AQUA));
            }

            tooltip.add(Component.translatable(
                    LangDefs.REPLACER_RADIUS_LABEL.getTranslationKey(),
                    getRadius(stack))
                    .withStyle(ChatFormatting.GRAY));

            ConnectivityMode mode = getConnectivityMode(stack);

            tooltip.add(Component.translatable(
                    mode == ConnectivityMode.DIRECT
                            ? LangDefs.REPLACER_CONNECTIVITY_DIRECT.getTranslationKey()
                            : LangDefs.REPLACER_CONNECTIVITY_DIAGONAL.getTranslationKey())
                    .withStyle(ChatFormatting.GRAY));

            if (IsModLoaded.AE2) {
                try {
                    GlobalPos ae2Pos = AE2GridLinkableHandler.getLinkedPos(stack);

                    if (ae2Pos != null) {
                        tooltip.add(Component.translatable(
                                LangDefs.REPLACER_LINKED_TO_AE2.getTranslationKey(),
                                ae2Pos.pos().getX()
                                        + " "
                                        + ae2Pos.pos().getY()
                                        + " "
                                        + ae2Pos.pos().getZ())
                                .withStyle(ChatFormatting.AQUA));

                        tooltip.add(Component.translatable(
                                LangDefs.REPLACER_LINK_DIMENSION.getTranslationKey(),
                                ae2Pos.dimension().location().toString())
                                .withStyle(ChatFormatting.GRAY));
                    }
                } catch (Throwable ignored) {
                }
            }
        } else {
            tooltip.add(Component.translatable(LangDefs.REPLACER_SELECT_TARGET_HINT.getTranslationKey())
                    .withStyle(ChatFormatting.GRAY));

            tooltip.add(Component.translatable(LangDefs.REPLACER_UNDO_HINT.getTranslationKey())
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
