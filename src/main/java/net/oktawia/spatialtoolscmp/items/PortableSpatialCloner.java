package net.oktawia.spatialtoolscmp.items;

import java.io.IOException;
import java.util.*;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;

import net.oktawia.spatialtoolscmp.IsModLoaded;
import net.oktawia.spatialtoolscmp.SpatialConfig;
import net.oktawia.spatialtoolscmp.compat.ae2.AE2GridLinkableHandler;
import net.oktawia.spatialtoolscmp.defs.LangDefs;
import net.oktawia.spatialtoolscmp.defs.SpatialMenuRegistrar;
import net.oktawia.spatialtoolscmp.items.helpers.*;
import net.oktawia.spatialtoolscmp.logic.*;
import net.oktawia.spatialtoolscmp.util.StructureToolKeys;
import net.oktawia.spatialtoolscmp.util.TemplateUtil;

public class PortableSpatialCloner extends AbstractStructureCaptureToolItem {

    private static final String NESTED_RESOURCE_MODE_KEY = "nestedResourceMode";

    public enum NestedInventoryResourceMode {
        NONE(0),
        PLAYER(1),
        CONNECTED(2),
        BOTH(3);

        private final int id;

        NestedInventoryResourceMode(int id) {
            this.id = id;
        }

        public int id() {
            return this.id;
        }

        public boolean usePlayerNested() {
            return this == PLAYER || this == BOTH;
        }

        public boolean useConnectedNested() {
            return this == CONNECTED || this == BOTH;
        }

        public NestedInventoryResourceMode next() {
            return switch (this) {
                case NONE -> PLAYER;
                case PLAYER -> CONNECTED;
                case CONNECTED -> BOTH;
                case BOTH -> NONE;
            };
        }

        public static NestedInventoryResourceMode byId(int id) {
            for (NestedInventoryResourceMode mode : values()) {
                if (mode.id == id) {
                    return mode;
                }
            }

            return NONE;
        }
    }

    public PortableSpatialCloner(Item.Properties properties) {
        super(SpatialConfig.COMMON.PORTABLE_SPATIAL_CLONER_BASE_INTERNAL_POWER_CAPACITY::get, 5, 4, properties);
    }

    public static ItemStack findHeld(@Nullable Player player) {
        return StructureToolUtil.findHeld(player, PortableSpatialCloner.class);
    }

    public static ItemStack findActive(@Nullable Player player) {
        return StructureToolUtil.findActive(player, PortableSpatialCloner.class);
    }

    public static NestedInventoryResourceMode getNestedInventoryResourceMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();

        if (tag == null || !tag.contains(NESTED_RESOURCE_MODE_KEY, Tag.TAG_ANY_NUMERIC)) {
            return NestedInventoryResourceMode.NONE;
        }

        return NestedInventoryResourceMode.byId(tag.getInt(NESTED_RESOURCE_MODE_KEY));
    }

    public static void setNestedInventoryResourceMode(ItemStack stack, NestedInventoryResourceMode mode) {
        if (stack.isEmpty() || mode == null) {
            return;
        }

        stack.getOrCreateTag().putInt(NESTED_RESOURCE_MODE_KEY, mode.id());
    }

    public static NestedInventoryResourceMode cycleNestedInventoryResourceMode(ItemStack stack) {
        NestedInventoryResourceMode next = getNestedInventoryResourceMode(stack).next();
        setNestedInventoryResourceMode(stack, next);
        return next;
    }

    public static boolean hasItemHandlerLink(ItemStack stack) {
        return ClonerItemHandlerLink.hasItemHandlerLink(stack);
    }

    public static void clearItemHandlerLink(ItemStack stack) {
        ClonerItemHandlerLink.clearItemHandlerLink(stack);
    }

    public static long countLinkedItemHandlerStorage(ServerLevel level, ItemStack toolStack, ItemStack wanted) {
        return ClonerItemHandlerLink.countLinkedItemHandlerStorage(level, toolStack, wanted);
    }

    public static long countNestedInventoryInPlayerInventory(
            ServerLevel level,
            Player player,
            ItemStack toolStack,
            ItemStack wanted) {
        return ClonerInventoryAccess.countNestedInventoryInPlayerInventory(level, player, toolStack, wanted);
    }

    public static long countNestedInventoryInLinkedItemHandlerStorage(
            ServerLevel level,
            ItemStack toolStack,
            ItemStack wanted) {
        return ClonerInventoryAccess.countNestedInventoryInLinkedItemHandlerStorage(level, toolStack, wanted);
    }

    @Override
    protected MenuType<?> getToolMenuType() {
        return SpatialMenuRegistrar.PORTABLE_SPATIAL_CLONER_MENU.get();
    }

    @Override
    protected boolean removeCapturedBlocks() {
        return false;
    }

    @Override
    protected Component getCaptureSuccessMessage() {
        return Component.translatable(LangDefs.STRUCTURE_COPIED_AND_SAVED.getTranslationKey());
    }

    @Override
    protected Component getStoredStructureActionNotImplementedMessage() {
        return Component.translatable(LangDefs.STRUCTURE_PASTED.getTranslationKey());
    }

    @Override
    protected double getPowerPerBlockCapture() {
        return SpatialConfig.COMMON.PORTABLE_SPATIAL_CLONER_COST.get();
    }

    @Override
    protected double getPowerPerBlockPaste() {
        return SpatialConfig.COMMON.PORTABLE_SPATIAL_CLONER_COST.get();
    }

    @Override
    protected int getMaxStructureSize() {
        return SpatialConfig.COMMON.PORTABLE_SPATIAL_CLONER_MAX_STRUCTURE_SIZE.get();
    }

    @Override
    protected double getEnergyCostMultiplier() {
        return SpatialConfig.energyCostMultiplier(SpatialConfig.COMMON.PORTABLE_SPATIAL_CLONER_ENERGY_COST_MULTIPLIER);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (hand == InteractionHand.OFF_HAND) {
            if (!level.isClientSide()) {
                undoLastClonerPaste((ServerLevel) level, player, stack);
            }

            return InteractionResultHolder.success(stack);
        }

        return super.use(level, player, hand);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();

        if (context.getHand() == InteractionHand.OFF_HAND && player != null) {
            if (player.isShiftKeyDown()) {
                if (!level.isClientSide()) {
                    tryLinkItemHandlerStorage(
                            (ServerLevel) level,
                            player,
                            context.getItemInHand(),
                            context.getClickedPos(),
                            context.getClickedFace());
                }

                return InteractionResult.sidedSuccess(level.isClientSide());
            }

            if (!level.isClientSide()) {
                undoLastClonerPaste((ServerLevel) level, player, context.getItemInHand());
            }

            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        return super.useOn(context);
    }

    @Override
    protected void onUseWithStoredStructure(ServerLevel level, Player player, ItemStack stack) {
        BlockPos anchoredOrigin = StructureToolStackState.getAnchorIfValid(stack, level.dimension());

        if (anchoredOrigin != null) {
            pasteBestEffort(level, player, stack, anchoredOrigin);
            return;
        }

        var hit = StructureToolUtil.rayTrace(level, player, 64.0D);

        if (hit.getType() != HitResult.Type.BLOCK) {
            showHud(player, Component.translatable(LangDefs.NO_BLOCK_IN_RANGE.getTranslationKey()));
            return;
        }

        BlockPos pasteOrigin = hit.getBlockPos().relative(hit.getDirection());
        pasteBestEffort(level, player, stack, pasteOrigin);
    }

    @Override
    protected void onUseOnWithStoredStructure(ServerLevel level, Player player, ItemStack stack,
            BlockPos clickedFacePos) {
        BlockPos anchoredOrigin = StructureToolStackState.getAnchorIfValid(stack, level.dimension());
        pasteBestEffort(level, player, stack, anchoredOrigin == null ? clickedFacePos : anchoredOrigin);
    }

    protected void pasteBestEffort(ServerLevel level, Player player, ItemStack toolStack, BlockPos origin) {
        String id = StructureToolStackState.getStructureId(toolStack);

        if (id.isBlank()) {
            return;
        }

        CompoundTag savedTag;

        try {
            savedTag = ClonerStructureLibraryStore.loadSelectedOrMigrateLegacy(
                    level.getServer(),
                    player.getUUID(),
                    toolStack);
        } catch (IOException exception) {
            showHud(player, Component.translatable(LangDefs.FAILED_TO_LOAD_STRUCTURE.getTranslationKey()));
            return;
        }

        if (savedTag == null) {
            showHud(player, Component.translatable(LangDefs.STORED_STRUCTURE_NOT_FOUND.getTranslationKey()));
            clearStoredStructure(player, toolStack);
            return;
        }

        if (isTemplateEmpty(savedTag)) {
            clearStoredStructure(player, toolStack);

            showHud(
                    player,
                    HUD_TIME_MEDIUM,
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_STORED_STRUCTURE_EMPTY.getTranslationKey())),
                    cyan(Component
                            .translatable(LangDefs.STRUCTURE_GADGET_INVALID_STRUCTURE_CLEARED.getTranslationKey())));

            return;
        }

        if (!checkStructureSizeLimit(player, savedTag)) {
            return;
        }

        BlockPos energyOrigin = TemplateUtil.getEnergyOrigin(savedTag);
        BlockPos templateOffset = TemplateUtil.getTemplateOffset(savedTag);

        double requiredPower = StructureToolUtil.calculatePreviewStructurePower(
                savedTag,
                energyOrigin.subtract(templateOffset),
                getPowerPerBlockPaste(),
                getEnergyCostMultiplier());

        if (!tryUsePower(player, toolStack, requiredPower)) {
            showNotEnoughPower(player, toolStack, requiredPower);
            return;
        }

        List<TemplateUtil.BlockInfo> rawBlocks = TemplateUtil.parseRawBlocksFromTag(savedTag);
        Map<BlockPos, CompoundTag> metadataByPos = parseMetadataByPos(savedTag);

        for (StructureCloneExtension extension : StructureToolExtensions.clonerExtensions()) {
            try {
                extension.onBeforePaste(level, player, rawBlocks);
            } catch (Throwable ignored) {
            }
        }

        List<ClonerUndoHandler.ClonerUndoPlacedBlock> undoPlacedBlocks = new ArrayList<>();

        Map<BlockPos, TemplateUtil.BlockInfo> blocksByLocalPos = new HashMap<>();

        for (TemplateUtil.BlockInfo blockInfo : rawBlocks) {
            blocksByLocalPos.put(blockInfo.pos(), blockInfo);
        }

        List<DeferredPaste> deferred = new ArrayList<>();

        int placed = 0;
        int skipped = 0;

        for (TemplateUtil.BlockInfo blockInfo : rawBlocks) {
            BlockPos localPos = blockInfo.pos();

            BlockPos worldPos = origin
                    .subtract(energyOrigin)
                    .offset(templateOffset)
                    .offset(localPos);

            CompoundTag blockMetadata = metadataByPos.get(localPos);

            if (ClonerBlockPlacer.shouldDeferPlacement(
                    level,
                    worldPos,
                    blockInfo.state(),
                    blockInfo.blockEntityTag(),
                    ClonerBlockPlacer.hasStructureNeighbourWithBlockEntity(blocksByLocalPos, localPos))) {
                deferred.add(new DeferredPaste(worldPos.immutable(), blockInfo, blockMetadata));
                continue;
            }

            if (pasteSingleBlock(level, player, toolStack, worldPos, blockInfo, blockMetadata, undoPlacedBlocks,
                    false)) {
                placed++;
            } else {
                skipped++;
            }
        }

        if (deferred.isEmpty()) {
            finishPaste(level, player, toolStack, undoPlacedBlocks, placed, skipped);
            return;
        }

        MinecraftServer server = level.getServer();
        int placedBeforeDeferred = placed;
        int skippedBeforeDeferred = skipped;

        server.tell(new TickTask(server.getTickCount() + 2, () -> {
            int placedTotal = placedBeforeDeferred;
            int skippedTotal = skippedBeforeDeferred;

            for (DeferredPaste entry : deferred) {
                if (pasteSingleBlock(
                        level,
                        player,
                        toolStack,
                        entry.worldPos(),
                        entry.blockInfo(),
                        entry.blockMetadata(),
                        undoPlacedBlocks,
                        true)) {
                    placedTotal++;
                } else {
                    skippedTotal++;
                }
            }

            finishPaste(level, player, toolStack, undoPlacedBlocks, placedTotal, skippedTotal);
        }));
    }

    private record DeferredPaste(
            BlockPos worldPos,
            TemplateUtil.BlockInfo blockInfo,
            @Nullable CompoundTag blockMetadata) {
    }

    private boolean topUpExistingBlockWithoutUndo(
            ServerLevel level,
            Player player,
            ItemStack toolStack,
            BlockPos worldPos,
            BlockState stateToPlace,
            @Nullable CompoundTag rawBeTag,
            @Nullable CompoundTag blockMetadata,
            ClonerPasteContext pasteContext) {
        List<ItemStack> consumedStacks = new ArrayList<>();
        boolean applied = false;

        for (StructureCloneExtension extension : StructureToolExtensions.clonerExtensions()) {
            try {
                if (extension.applyToExistingBlock(
                        level,
                        player,
                        worldPos,
                        stateToPlace,
                        rawBeTag,
                        blockMetadata,
                        pasteContext,
                        consumedStacks)) {
                    applied = true;
                    break;
                }
            } catch (Throwable ignored) {
            }
        }

        if (!applied) {
            return false;
        }

        if (!player.isCreative()) {
            for (ItemStack cost : consumedStacks) {
                if (cost.isEmpty()) {
                    continue;
                }

                ClonerInventoryAccess.consumeForPaste(level, player, toolStack, cost, cost.getCount());
            }
        }

        return true;
    }

    private boolean pasteSingleBlock(
            ServerLevel level,
            Player player,
            ItemStack toolStack,
            BlockPos worldPos,
            TemplateUtil.BlockInfo blockInfo,
            @Nullable CompoundTag blockMetadata,
            List<ClonerUndoHandler.ClonerUndoPlacedBlock> undoPlacedBlocks,
            boolean supportDependent) {
        BlockState stateToPlace = blockInfo.state();
        CompoundTag rawBeTag = blockInfo.blockEntityTag();

        List<ItemStack> refundStacks = new ArrayList<>();
        ClonerPasteContext pasteContext = new TrackingPasteContext(level, player, toolStack, refundStacks);

        if (level.getBlockState(worldPos).getBlock() == stateToPlace.getBlock()) {
            return topUpExistingBlockWithoutUndo(
                    level,
                    player,
                    toolStack,
                    worldPos,
                    stateToPlace,
                    rawBeTag,
                    blockMetadata,
                    pasteContext);
        }

        PlacementPlan selectedPlan = null;

        for (StructureCloneExtension extension : StructureToolExtensions.clonerExtensions()) {
            Optional<PlacementPlan> extensionPlan = extension.buildPlacementPlan(
                    level,
                    player,
                    stateToPlace,
                    rawBeTag,
                    blockMetadata,
                    pasteContext);

            if (extensionPlan.isPresent()) {
                selectedPlan = extensionPlan.get();
                break;
            }
        }

        boolean success;

        if (selectedPlan != null) {
            success = ClonerBlockPlacer.placePlannedBlockBestEffort(
                    level,
                    worldPos,
                    selectedPlan,
                    player,
                    toolStack);

            if (success) {
                for (ItemStack cost : selectedPlan.consumedStacks()) {
                    if (!cost.isEmpty()) {
                        refundStacks.add(cost.copy());
                    }
                }
            }
        } else {
            success = ClonerBlockPlacer.placeRegularBlockBestEffort(
                    level,
                    worldPos,
                    stateToPlace,
                    player,
                    toolStack);

            if (success) {
                ItemStack required = ClonerBlockPlacer.getRequiredBlockItem(stateToPlace);

                if (!required.isEmpty()) {
                    refundStacks.add(required.copy());
                }
            }
        }

        if (!success) {
            if (!refundStacks.isEmpty()) {
                ClonerInventoryAccess.refundStacksToAeThenInventory(level, player, toolStack, refundStacks);
            }

            return false;
        }

        BlockState finalState = level.getBlockState(worldPos);
        BlockPos worldPosImmutable = worldPos.immutable();

        for (StructureCloneExtension extension : StructureToolExtensions.clonerExtensions()) {
            extension.onBlockPlaced(level, player, worldPosImmutable,
                    level.getBlockEntity(worldPosImmutable), blockMetadata);
        }

        undoPlacedBlocks.add(new ClonerUndoHandler.ClonerUndoPlacedBlock(
                worldPosImmutable,
                finalState.toString(),
                List.copyOf(ClonerUndoHandler.aggregateRefundStacks(refundStacks)),
                List.of(),
                supportDependent));

        return true;
    }

    private void finishPaste(
            ServerLevel level,
            Player player,
            ItemStack toolStack,
            List<ClonerUndoHandler.ClonerUndoPlacedBlock> undoPlacedBlocks,
            int placed,
            int skipped) {
        if (placed > 0) {
            ClonerBlockPlacer.finishPlacement(
                    level,
                    undoPlacedBlocks.stream().map(ClonerUndoHandler.ClonerUndoPlacedBlock::pos).toList());

            ClonerUndoHandler.store(toolStack, level, undoPlacedBlocks);

            StructureToolStackState.setRotatePanelMode(toolStack, false);

            showHud(
                    player,
                    HUD_TIME_MEDIUM,
                    cyan(Component.translatable(LangDefs.STRUCTURE_PASTED.getTranslationKey())),
                    skipped > 0
                            ? red(Component.translatable(LangDefs.STRUCTURE_GADGET_PLACED_SKIPPED.getTranslationKey(),
                                    placed, skipped))
                            : cyan(Component.translatable(LangDefs.STRUCTURE_GADGET_PLACED_SKIPPED.getTranslationKey(),
                                    placed, skipped)),
                    cyan(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO_HINT.getTranslationKey())));
        } else {
            showHud(
                    player,
                    HUD_TIME_MEDIUM,
                    red(Component.translatable(LangDefs.FAILED_TO_PASTE_STRUCTURE.getTranslationKey())),
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_SKIPPED.getTranslationKey(), skipped)));
        }
    }

    private void undoLastClonerPaste(ServerLevel level, Player player, ItemStack toolStack) {
        CompoundTag stackTag = toolStack.getTag();

        if (stackTag == null || !stackTag.contains(ClonerUndoHandler.UNDO_ID_KEY, Tag.TAG_STRING)) {
            showHud(
                    player,
                    HUD_TIME_MEDIUM,
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO.getTranslationKey())),
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_NOTHING_TO_UNDO.getTranslationKey())));
            return;
        }

        CompoundTag undoTag = ClonerUndoHandler.load(level, toolStack);

        if (undoTag == null || !ClonerUndoHandler.hasBlocks(undoTag)) {
            ClonerUndoHandler.clear(level, toolStack);

            showHud(
                    player,
                    HUD_TIME_MEDIUM,
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO.getTranslationKey())),
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO_INVALID_CLEARED.getTranslationKey())));
            return;
        }

        String undoDimension = ClonerUndoHandler.getDimension(undoTag);
        String currentDimension = level.dimension().location().toString();

        if (!currentDimension.equals(undoDimension)) {
            showHud(
                    player,
                    HUD_TIME_MEDIUM,
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO.getTranslationKey())),
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO_OTHER_DIMENSION.getTranslationKey())));
            return;
        }

        List<ClonerUndoHandler.ClonerUndoPlacedBlock> undoBlocks = ClonerUndoHandler.readBlocks(undoTag);

        if (undoBlocks.isEmpty()) {
            ClonerUndoHandler.clear(level, toolStack);

            showHud(
                    player,
                    HUD_TIME_MEDIUM,
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO.getTranslationKey())),
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO_NOTHING_PLACED.getTranslationKey())));
            return;
        }

        List<ClonerUndoHandler.ClonerUndoPlacedBlock> undoableBlocks = ClonerUndoHandler.filterUndoableBlocks(level,
                undoBlocks);

        if (undoableBlocks == null) {
            showHud(
                    player,
                    HUD_TIME_MEDIUM,
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO.getTranslationKey())),
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO_WORLD_CHANGED.getTranslationKey())));
            return;
        }

        undoBlocks = undoableBlocks;

        List<ItemStack> refundStacks = ClonerUndoHandler.collectCurrentRefundStacks(level, undoBlocks);
        boolean shouldRefundItems = !player.isCreative() && !refundStacks.isEmpty();

        if (shouldRefundItems && !ClonerInventoryAccess.canStoreRefundStacks(level, player, toolStack, refundStacks)) {
            showHud(
                    player,
                    HUD_TIME_MEDIUM,
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO.getTranslationKey())),
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO_NO_SPACE.getTranslationKey())));
            return;
        }

        ClonerUndoHandler.removeBlocks(level, undoBlocks);

        ClonerInventoryAccess.ClonerRefundResult refundResult = ClonerInventoryAccess.ClonerRefundResult.success(false);

        if (shouldRefundItems) {
            refundResult = ClonerInventoryAccess.refundStacksToAeThenInventory(level, player, toolStack, refundStacks);

            if (!refundResult.success()) {
                showHud(
                        player,
                        HUD_TIME_MEDIUM,
                        red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO.getTranslationKey())),
                        red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO_NO_SPACE.getTranslationKey())));
                return;
            }
        }

        ClonerUndoHandler.clear(level, toolStack);

        if (shouldRefundItems) {
            showHud(
                    player,
                    HUD_TIME_MEDIUM,
                    cyan(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO.getTranslationKey())),
                    cyan(Component.translatable(LangDefs.STRUCTURE_GADGET_COPY_PASTE_UNDONE.getTranslationKey())),
                    cyan(Component.translatable(
                            refundResult.insertedIntoMe()
                                    ? LangDefs.STRUCTURE_GADGET_ITEMS_REFUNDED_TO_ME.getTranslationKey()
                                    : LangDefs.STRUCTURE_GADGET_ITEMS_REFUNDED.getTranslationKey())));
        } else {
            showHud(
                    player,
                    HUD_TIME_MEDIUM,
                    cyan(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO.getTranslationKey())),
                    cyan(Component.translatable(LangDefs.STRUCTURE_GADGET_COPY_PASTE_UNDONE.getTranslationKey())));
        }
    }

    private void tryLinkItemHandlerStorage(
            ServerLevel level,
            Player player,
            ItemStack toolStack,
            BlockPos pos,
            @Nullable Direction side) {
        var blockEntity = level.getBlockEntity(pos);

        if (blockEntity == null || !ClonerItemHandlerLink.hasItemHandlerCapability(blockEntity, side)) {
            showHud(
                    player,
                    HUD_TIME_MEDIUM,
                    red(Component.translatable(LangDefs.PORTABLE_SPATIAL_CLONER_NO_ITEM_HANDLER.getTranslationKey())),
                    cyan(Component.translatable(
                            LangDefs.PORTABLE_SPATIAL_CLONER_LINK_DIMENSION.getTranslationKey(),
                            level.dimension().location().toString())));
            return;
        }

        ClonerItemHandlerLink.setItemHandlerLink(toolStack, level, pos, side);

        if (IsModLoaded.AE2) {
            try {
                AE2GridLinkableHandler.INSTANCE.unlink(toolStack);
            } catch (Throwable ignored) {
            }
        }

        showHud(
                player,
                HUD_TIME_MEDIUM,
                cyan(Component.translatable(
                        LangDefs.PORTABLE_SPATIAL_CLONER_LINKED_TO.getTranslationKey(),
                        pos.getX() + " " + pos.getY() + " " + pos.getZ())),
                cyan(Component.translatable(
                        LangDefs.PORTABLE_SPATIAL_CLONER_LINK_DIMENSION.getTranslationKey(),
                        level.dimension().location().toString())));
    }

    private void clearStoredStructure(Player player, ItemStack toolStack) {
        StructureToolStackState.clearSelectedClonerLibraryEntry(toolStack);
        StructureToolStackState.clearSelection(toolStack);
        StructureToolStackState.resetPreviewSideMap(toolStack);

        TemplateUtil.setTemplateOffset(toolStack.getOrCreateTag(), BlockPos.ZERO);
        TemplateUtil.setEnergyOrigin(toolStack.getOrCreateTag(), BlockPos.ZERO);

        if (player instanceof ServerPlayer serverPlayer) {
            StructureToolPreviewDispatcher.sendPreviewToPlayer(serverPlayer, null);
        }
    }

    protected Map<BlockPos, CompoundTag> parseMetadataByPos(CompoundTag savedTag) {
        Map<BlockPos, CompoundTag> out = new HashMap<>();

        if (!savedTag.contains(StructureToolKeys.CLONE_METADATA_KEY, Tag.TAG_COMPOUND)) {
            return out;
        }

        CompoundTag metadata = savedTag.getCompound(StructureToolKeys.CLONE_METADATA_KEY);

        if (!metadata.contains(StructureToolKeys.CLONE_METADATA_BLOCKS_KEY, Tag.TAG_LIST)) {
            return out;
        }

        ListTag blocks = metadata.getList(StructureToolKeys.CLONE_METADATA_BLOCKS_KEY, Tag.TAG_COMPOUND);

        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag blockEntry = blocks.getCompound(i);

            if (!blockEntry.contains(StructureToolKeys.CLONE_KEY_POS, Tag.TAG_COMPOUND)) {
                continue;
            }

            CompoundTag posTag = blockEntry.getCompound(StructureToolKeys.CLONE_KEY_POS);
            BlockPos pos = new BlockPos(posTag.getInt("x"), posTag.getInt("y"), posTag.getInt("z"));

            out.put(pos, blockEntry);
        }

        return out;
    }

    @Override
    protected String saveCapturedStructure(
            ServerLevel level,
            Player player,
            ItemStack stack,
            CompoundTag savedTag) throws IOException {
        ClonerStructureLibraryStore.Entry entry = ClonerStructureLibraryStore.saveForCurrentSelection(
                level.getServer(),
                player.getUUID(),
                stack,
                savedTag);

        StructureToolStackState.setSelectedClonerLibraryEntry(
                stack,
                player.getUUID(),
                entry.id());

        return entry.id();
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        if (!Screen.hasShiftDown()) {
            tooltip.add(Component.translatable(
                    LangDefs.PORTABLE_SPATIAL_CLONER_SHIFT_FOR_DETAILS.getTranslationKey())
                    .withStyle(ChatFormatting.DARK_GRAY));

            return;
        }

        tooltip.add(Component.translatable(
                LangDefs.PORTABLE_SPATIAL_CLONER_LINK_STORAGE_TOOLTIP.getTranslationKey())
                .withStyle(ChatFormatting.GRAY));

        ClonerItemHandlerLink.ItemHandlerLink itemHandlerLink = ClonerItemHandlerLink.getItemHandlerLink(stack);

        if (itemHandlerLink != null) {
            BlockPos linkPos = itemHandlerLink.pos().pos();

            tooltip.add(Component.translatable(
                    LangDefs.PORTABLE_SPATIAL_CLONER_LINKED_TO.getTranslationKey(),
                    linkPos.getX() + " " + linkPos.getY() + " " + linkPos.getZ()).withStyle(ChatFormatting.AQUA));

            tooltip.add(Component.translatable(
                    LangDefs.PORTABLE_SPATIAL_CLONER_LINK_DIMENSION.getTranslationKey(),
                    itemHandlerLink.pos().dimension().location().toString()).withStyle(ChatFormatting.GRAY));

            return;
        }

        if (IsModLoaded.AE2) {
            try {
                GlobalPos ae2Pos = AE2GridLinkableHandler.getLinkedPos(stack);

                if (ae2Pos != null) {
                    tooltip.add(Component.translatable(
                            LangDefs.PORTABLE_SPATIAL_CLONER_LINKED_TO_AE2.getTranslationKey(),
                            ae2Pos.pos().getX() + " " + ae2Pos.pos().getY() + " " + ae2Pos.pos().getZ())
                            .withStyle(ChatFormatting.AQUA));

                    tooltip.add(Component.translatable(
                            LangDefs.PORTABLE_SPATIAL_CLONER_LINK_DIMENSION.getTranslationKey(),
                            ae2Pos.dimension().location().toString()).withStyle(ChatFormatting.GRAY));
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static final class TrackingPasteContext implements ClonerPasteContext {
        private final ServerLevel level;
        private final Player player;
        private final ItemStack toolStack;
        private final List<ItemStack> consumedStacks;

        private TrackingPasteContext(
                ServerLevel level,
                Player player,
                ItemStack toolStack,
                List<ItemStack> consumedStacks) {
            this.level = level;
            this.player = player;
            this.toolStack = toolStack;
            this.consumedStacks = consumedStacks;
        }

        @Override
        public long countAvailableForPaste(ItemStack wanted) {
            return ClonerInventoryAccess.countAvailableForPaste(level, player, toolStack, wanted);
        }

        @Override
        public boolean canReserveForPaste(Map<Item, Integer> reserved, ItemStack wanted, int amount) {
            return ClonerInventoryAccess.canReserveForPaste(level, player, toolStack, reserved, wanted, amount);
        }

        @Override
        public boolean consumeForPaste(ItemStack wanted, int amount) {
            boolean success = ClonerInventoryAccess.consumeForPaste(level, player, toolStack, wanted, amount);

            if (success && !wanted.isEmpty() && amount > 0) {
                ItemStack copy = wanted.copy();
                copy.setCount(amount);
                consumedStacks.add(copy);
            }

            return success;
        }

        @Override
        public boolean placeBlockAndLoadTag(BlockPos pos, BlockState state, @Nullable CompoundTag rawBeTag) {
            return ClonerBlockPlacer.placeBlockAndLoadTag(level, pos, state, rawBeTag);
        }

        @Override
        public boolean hasCollision(BlockState existing, BlockState target) {
            return ClonerBlockPlacer.hasCollision(existing, target);
        }

        @Override
        public ItemStack getRequiredBlockItem(BlockState state) {
            return ClonerBlockPlacer.getRequiredBlockItem(state);
        }
    }
}
