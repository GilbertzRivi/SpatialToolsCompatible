package net.oktawia.spatialtoolscmp.items;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.oktawia.spatialtoolscmp.SpatialConfig;
import net.oktawia.spatialtoolscmp.defs.LangDefs;
import net.oktawia.spatialtoolscmp.defs.SpatialMenuRegistrar;
import net.oktawia.spatialtoolscmp.logic.StructureToolExtensions;
import net.oktawia.spatialtoolscmp.logic.StructureToolPreviewDispatcher;
import net.oktawia.spatialtoolscmp.logic.StructureToolStackState;
import net.oktawia.spatialtoolscmp.logic.StructureToolStructureStore;
import net.oktawia.spatialtoolscmp.logic.StructureToolUtil;
import net.oktawia.spatialtoolscmp.util.TemplateUtil;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PortableSpatialStorage extends AbstractStructureCaptureToolItem {

    private static final String STORAGE_UNDO_TYPE_KEY = "storageUndoType";
    private static final String STORAGE_UNDO_DIMENSION_KEY = "storageUndoDimension";
    private static final String STORAGE_UNDO_ID_KEY = "storageUndoId";

    private static final String STORAGE_UNDO_TYPE_CUT = "cut";
    private static final String STORAGE_UNDO_TYPE_PASTE = "paste";

    private static final String STORAGE_UNDO_ORIGIN_X_KEY = "storageUndoOriginX";
    private static final String STORAGE_UNDO_ORIGIN_Y_KEY = "storageUndoOriginY";
    private static final String STORAGE_UNDO_ORIGIN_Z_KEY = "storageUndoOriginZ";

    private static final String STORAGE_UNDO_MIN_X_KEY = "storageUndoMinX";
    private static final String STORAGE_UNDO_MIN_Y_KEY = "storageUndoMinY";
    private static final String STORAGE_UNDO_MIN_Z_KEY = "storageUndoMinZ";

    private static final String STORAGE_UNDO_MAX_X_KEY = "storageUndoMaxX";
    private static final String STORAGE_UNDO_MAX_Y_KEY = "storageUndoMaxY";
    private static final String STORAGE_UNDO_MAX_Z_KEY = "storageUndoMaxZ";

    private static final String STORAGE_UNDO_BLOCKS_KEY = "storageUndoBlocks";
    private static final String STORAGE_UNDO_TEMPLATE_KEY = "storageUndoTemplate";

    private static final String STORAGE_UNDO_POS_X_KEY = "x";
    private static final String STORAGE_UNDO_POS_Y_KEY = "y";
    private static final String STORAGE_UNDO_POS_Z_KEY = "z";
    private static final String STORAGE_UNDO_STATE_KEY = "state";

    private static final int STORAGE_UNDO_CLEAR_FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    public PortableSpatialStorage(Item.Properties properties) {
        super(SpatialConfig.COMMON.PORTABLE_SPATIAL_STORAGE_BASE_INTERNAL_POWER_CAPACITY::get, 4, 4, properties);
    }

    public static BlockHitResult rayTrace(Level level, Player player, double maxDistance) {
        return StructureToolUtil.rayTrace(level, player, maxDistance);
    }

    @Override
    protected MenuType<?> getToolMenuType() {
        return SpatialMenuRegistrar.PORTABLE_SPATIAL_STORAGE_MENU.get();
    }

    @Override
    protected boolean removeCapturedBlocks() {
        return true;
    }

    @Override
    protected Component getCaptureSuccessMessage() {
        return Component.translatable(LangDefs.STRUCTURE_CUT_AND_SAVED.getTranslationKey());
    }

    @Override
    protected Component getStoredStructureActionNotImplementedMessage() {
        return Component.translatable(LangDefs.STRUCTURE_PASTED.getTranslationKey());
    }

    @Override
    protected double getPowerPerBlockCapture() {
        return SpatialConfig.COMMON.PORTABLE_SPATIAL_STORAGE_COST.get();
    }

    @Override
    protected double getPowerPerBlockPaste() {
        return SpatialConfig.COMMON.PORTABLE_SPATIAL_STORAGE_COST.get();
    }

    @Override
    protected int getMaxStructureSize() {
        return SpatialConfig.COMMON.PORTABLE_SPATIAL_STORAGE_MAX_STRUCTURE_SIZE.get();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (hand == InteractionHand.OFF_HAND && isToolEnabled()) {
            if (!level.isClientSide()) {
                undoLastStorageAction((ServerLevel) level, player, stack);
            }

            return InteractionResultHolder.success(stack);
        }

        return super.use(level, player, hand);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();

        if (context.getHand() == InteractionHand.OFF_HAND && isToolEnabled() && player != null) {
            if (!level.isClientSide()) {
                undoLastStorageAction((ServerLevel) level, player, context.getItemInHand());
            }

            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        return super.useOn(context);
    }

    @Override
    protected void afterStructureCaptured(
            ServerLevel level,
            Player player,
            ItemStack stack,
            CapturedStructureResult result
    ) {
        storeUndoCut(
                stack,
                level,
                result.origin(),
                result.min(),
                result.max()
        );
    }

    @Override
    protected void onUseWithStoredStructure(ServerLevel level, Player player, ItemStack stack) {
        BlockHitResult hit = rayTrace(level, player, 50.0D);

        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pasteOrigin = hit.getBlockPos().relative(hit.getDirection());
            paste(level, player, stack, pasteOrigin);
        } else {
            showHud(player, Component.translatable(LangDefs.NO_BLOCK_IN_RANGE.getTranslationKey()));
        }
    }

    @Override
    protected void onUseOnWithStoredStructure(
            ServerLevel level,
            Player player,
            ItemStack stack,
            BlockPos clickedFacePos
    ) {
        paste(level, player, stack, clickedFacePos);
    }

    private void paste(ServerLevel level, Player player, ItemStack stack, BlockPos origin) {
        pasteInternal(
                level,
                player,
                stack,
                origin,
                true,
                true,
                true
        );
    }

    private boolean pasteInternal(
            ServerLevel level,
            Player player,
            ItemStack stack,
            BlockPos origin,
            boolean clearAfterPaste,
            boolean recordUndoPaste,
            boolean showSuccess
    ) {
        String id = StructureToolStackState.getStructureId(stack);

        if (id.isBlank()) {
            return false;
        }

        CompoundTag savedTag;

        try {
            savedTag = StructureToolStructureStore.load(level.getServer(), id);
        } catch (IOException exception) {
            showHud(player, Component.translatable(LangDefs.FAILED_TO_LOAD_STRUCTURE.getTranslationKey()));
            return false;
        }

        if (savedTag == null) {
            showHud(player, Component.translatable(LangDefs.STORED_STRUCTURE_NOT_FOUND.getTranslationKey()));
            clearStoredStructure(level, player, stack, id);
            return false;
        }

        if (isTemplateEmpty(savedTag)) {
            clearStoredStructure(level, player, stack, id);

            showHud(
                    player,
                    HUD_TIME_MEDIUM,
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_STORED_STRUCTURE_EMPTY.getTranslationKey())),
                    cyan(Component.translatable(LangDefs.STRUCTURE_GADGET_INVALID_STRUCTURE_CLEARED.getTranslationKey()))
            );

            return false;
        }

        if (!checkStructureSizeLimit(player, savedTag)) {
            return false;
        }

        if (hasPlacementCollision(level, savedTag, origin)) {
            showHud(player, Component.translatable(LangDefs.PASTE_COLLISION.getTranslationKey()));
            return false;
        }

        StructureTemplate template = new StructureTemplate();
        template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK), savedTag);

        BlockPos energyOrigin = TemplateUtil.getEnergyOrigin(savedTag);
        BlockPos templateOffset = TemplateUtil.getTemplateOffset(savedTag);
        BlockPos placementOrigin = origin.subtract(energyOrigin).offset(templateOffset);

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setIgnoreEntities(true);

        boolean placed = template.placeInWorld(level, placementOrigin, placementOrigin, settings, level.random, 3);

        if (!placed) {
            showHud(player, Component.translatable(LangDefs.FAILED_TO_PASTE_STRUCTURE.getTranslationKey()));
            return false;
        }

        StructureToolExtensions.notifyTemplatePasted(level, placementOrigin, savedTag);

        if (recordUndoPaste) {
            List<StorageUndoPlacedBlock> undoBlocks = collectUndoPlacedBlocksAfterPaste(
                    level,
                    savedTag,
                    origin
            );

            if (!undoBlocks.isEmpty()) {
                storeUndoPaste(
                        stack,
                        level,
                        origin,
                        savedTag,
                        undoBlocks
                );
            }
        }

        if (clearAfterPaste) {
            clearStoredStructure(level, player, stack, id);
        }

        if (showSuccess) {
            showHud(
                    player,
                    HUD_TIME_MEDIUM,
                    cyan(Component.translatable(LangDefs.STRUCTURE_PASTED.getTranslationKey())),
                    cyan(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO_HINT.getTranslationKey()))
            );
        }

        return true;
    }

    private void undoLastStorageAction(ServerLevel level, Player player, ItemStack stack) {
        CompoundTag stackTag = stack.getTag();

        if (stackTag == null || !stackTag.contains(STORAGE_UNDO_ID_KEY, Tag.TAG_STRING)) {
            showHud(
                    player,
                    HUD_TIME_MEDIUM,
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO.getTranslationKey())),
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_NOTHING_TO_UNDO.getTranslationKey()))
            );
            return;
        }

        CompoundTag undoTag = loadStorageUndoTag(level, stack);

        if (undoTag == null || !undoTag.contains(STORAGE_UNDO_TYPE_KEY, Tag.TAG_STRING)) {
            clearStorageUndo(level, stack);

            showHud(
                    player,
                    HUD_TIME_MEDIUM,
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO.getTranslationKey())),
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO_INVALID_CLEARED.getTranslationKey()))
            );
            return;
        }

        String dimension = undoTag.getString(STORAGE_UNDO_DIMENSION_KEY);
        String currentDimension = level.dimension().location().toString();

        if (!currentDimension.equals(dimension)) {
            showHud(
                    player,
                    HUD_TIME_MEDIUM,
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO.getTranslationKey())),
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO_OTHER_DIMENSION.getTranslationKey()))
            );
            return;
        }

        String type = undoTag.getString(STORAGE_UNDO_TYPE_KEY);

        if (STORAGE_UNDO_TYPE_CUT.equals(type)) {
            undoCut(level, player, stack, undoTag);
            return;
        }

        if (STORAGE_UNDO_TYPE_PASTE.equals(type)) {
            undoPaste(level, player, stack, undoTag);
            return;
        }

        clearStorageUndo(level, stack);

        showHud(
                player,
                HUD_TIME_MEDIUM,
                red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO.getTranslationKey())),
                red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO_INVALID_CLEARED.getTranslationKey()))
        );
    }

    private void undoCut(ServerLevel level, Player player, ItemStack stack, CompoundTag undoTag) {
        BlockPos origin = readUndoOrigin(undoTag);

        if (!StructureToolStackState.hasStructure(stack)) {
            clearStorageUndo(level, stack);

            showHud(
                    player,
                    HUD_TIME_MEDIUM,
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO.getTranslationKey())),
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_CANNOT_UNDO_CUT.getTranslationKey())),
                    cyan(Component.translatable(LangDefs.STRUCTURE_GADGET_STORED_STRUCTURE_MISSING.getTranslationKey()))
            );

            return;
        }

        boolean success = pasteInternal(
                level,
                player,
                stack,
                origin,
                true,
                false,
                false
        );

        if (!success) {
            return;
        }

        clearStorageUndo(level, stack);

        showHud(
                player,
                HUD_TIME_MEDIUM,
                cyan(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO.getTranslationKey())),
                cyan(Component.translatable(LangDefs.STRUCTURE_GADGET_CUT_UNDONE.getTranslationKey()))
        );
    }

    private void undoPaste(ServerLevel level, Player player, ItemStack stack, CompoundTag undoTag) {
        CompoundTag undoTemplate = readUndoTemplate(undoTag);

        if (undoTemplate == null || isTemplateEmpty(undoTemplate)) {
            clearStorageUndo(level, stack);

            showHud(
                    player,
                    HUD_TIME_MEDIUM,
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO.getTranslationKey())),
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO_INVALID_CLEARED.getTranslationKey()))
            );
            return;
        }

        List<StorageUndoPlacedBlock> undoBlocks = readUndoPlacedBlocks(undoTag);

        if (undoBlocks.isEmpty()) {
            clearStorageUndo(level, stack);

            showHud(
                    player,
                    HUD_TIME_MEDIUM,
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO.getTranslationKey())),
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO_NOTHING_PLACED.getTranslationKey()))
            );
            return;
        }

        if (!areUndoPlacedBlocksUnchanged(level, undoBlocks)) {
            showHud(
                    player,
                    HUD_TIME_MEDIUM,
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO.getTranslationKey())),
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO_WORLD_CHANGED.getTranslationKey()))
            );
            return;
        }

        removeUndoPlacedBlocks(level, undoBlocks);

        String newId;

        try {
            newId = saveCapturedStructure(level, player, stack, undoTemplate.copy());
        } catch (IOException exception) {
            showHud(player, Component.translatable(LangDefs.FAILED_TO_SAVE_STRUCTURE.getTranslationKey()));
            return;
        }

        StructureToolStackState.setStructureId(stack, newId);
        clearSelectionState(stack);

        restorePreviewTransformFromTemplate(stack, undoTemplate);
        syncToolStackToClient(player);

        if (player instanceof ServerPlayer serverPlayer) {
            StructureToolPreviewDispatcher.sendPreviewToPlayer(serverPlayer, undoTemplate);
        }

        clearStorageUndo(level, stack);

        showHud(
                player,
                HUD_TIME_MEDIUM,
                cyan(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO.getTranslationKey())),
                cyan(Component.translatable(LangDefs.STRUCTURE_GADGET_PASTE_UNDONE.getTranslationKey())),
                cyan(Component.translatable(LangDefs.STRUCTURE_GADGET_STRUCTURE_CUT_BACK.getTranslationKey()))
        );
    }

    private static void restorePreviewTransformFromTemplate(ItemStack stack, CompoundTag templateTag) {
        CompoundTag stackTag = stack.getOrCreateTag();

        TemplateUtil.setTemplateOffset(stackTag, TemplateUtil.getTemplateOffset(templateTag));
        TemplateUtil.setEnergyOrigin(stackTag, TemplateUtil.getEnergyOrigin(templateTag));
        TemplateUtil.copyPreviewTransformState(templateTag, stackTag);
    }

    private static void syncToolStackToClient(Player player) {
        player.getInventory().setChanged();

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.containerMenu.broadcastChanges();
            serverPlayer.inventoryMenu.broadcastChanges();
        }
    }

    private void storeUndoCut(
            ItemStack stack,
            ServerLevel level,
            BlockPos origin,
            BlockPos min,
            BlockPos max
    ) {
        CompoundTag undoTag = new CompoundTag();

        undoTag.putString(STORAGE_UNDO_TYPE_KEY, STORAGE_UNDO_TYPE_CUT);
        undoTag.putString(STORAGE_UNDO_DIMENSION_KEY, level.dimension().location().toString());

        writeUndoOrigin(undoTag, origin);
        writeUndoMin(undoTag, min);
        writeUndoMax(undoTag, max);

        storeStorageUndoData(stack, level, undoTag);
    }

    private void storeUndoPaste(
            ItemStack stack,
            ServerLevel level,
            BlockPos origin,
            CompoundTag templateTag,
            List<StorageUndoPlacedBlock> placedBlocks
    ) {
        CompoundTag undoTag = new CompoundTag();

        undoTag.putString(STORAGE_UNDO_TYPE_KEY, STORAGE_UNDO_TYPE_PASTE);
        undoTag.putString(STORAGE_UNDO_DIMENSION_KEY, level.dimension().location().toString());

        writeUndoOrigin(undoTag, origin);

        BlockBounds bounds = computeBounds(placedBlocks);

        if (bounds != null) {
            writeUndoMin(undoTag, bounds.min());
            writeUndoMax(undoTag, bounds.max());
        }

        undoTag.put(STORAGE_UNDO_TEMPLATE_KEY, templateTag.copy());

        ListTag blocksTag = new ListTag();

        for (StorageUndoPlacedBlock placedBlock : placedBlocks) {
            CompoundTag blockTag = new CompoundTag();

            blockTag.putInt(STORAGE_UNDO_POS_X_KEY, placedBlock.pos().getX());
            blockTag.putInt(STORAGE_UNDO_POS_Y_KEY, placedBlock.pos().getY());
            blockTag.putInt(STORAGE_UNDO_POS_Z_KEY, placedBlock.pos().getZ());
            blockTag.putString(STORAGE_UNDO_STATE_KEY, placedBlock.stateSignature());

            blocksTag.add(blockTag);
        }

        undoTag.put(STORAGE_UNDO_BLOCKS_KEY, blocksTag);

        storeStorageUndoData(stack, level, undoTag);
    }

    private void storeStorageUndoData(
            ItemStack stack,
            ServerLevel level,
            CompoundTag undoTag
    ) {
        clearStorageUndo(level, stack);

        String undoId = UUID.randomUUID().toString();

        try {
            StructureToolStructureStore.save(level.getServer(), undoId, undoTag);
            stack.getOrCreateTag().putString(STORAGE_UNDO_ID_KEY, undoId);
        } catch (IOException ignored) {
        }
    }

    private CompoundTag loadStorageUndoTag(ServerLevel level, ItemStack stack) {
        CompoundTag tag = stack.getTag();

        if (tag == null || !tag.contains(STORAGE_UNDO_ID_KEY, Tag.TAG_STRING)) {
            return null;
        }

        String undoId = tag.getString(STORAGE_UNDO_ID_KEY);

        if (undoId.isBlank()) {
            return null;
        }

        try {
            return StructureToolStructureStore.load(level.getServer(), undoId);
        } catch (IOException ignored) {
            return null;
        }
    }

    private void clearStorageUndo(ServerLevel level, ItemStack stack) {
        CompoundTag tag = stack.getTag();

        if (tag == null) {
            return;
        }

        if (tag.contains(STORAGE_UNDO_ID_KEY, Tag.TAG_STRING)) {
            String undoId = tag.getString(STORAGE_UNDO_ID_KEY);

            if (!undoId.isBlank()) {
                try {
                    StructureToolStructureStore.delete(level.getServer(), undoId);
                } catch (IOException ignored) {
                }
            }
        }

        clearStorageUndo(stack);
    }

    private static void clearStorageUndo(ItemStack stack) {
        CompoundTag tag = stack.getTag();

        if (tag == null) {
            return;
        }

        tag.remove(STORAGE_UNDO_ID_KEY);

        tag.remove(STORAGE_UNDO_TYPE_KEY);
        tag.remove(STORAGE_UNDO_DIMENSION_KEY);

        tag.remove(STORAGE_UNDO_ORIGIN_X_KEY);
        tag.remove(STORAGE_UNDO_ORIGIN_Y_KEY);
        tag.remove(STORAGE_UNDO_ORIGIN_Z_KEY);

        tag.remove(STORAGE_UNDO_MIN_X_KEY);
        tag.remove(STORAGE_UNDO_MIN_Y_KEY);
        tag.remove(STORAGE_UNDO_MIN_Z_KEY);

        tag.remove(STORAGE_UNDO_MAX_X_KEY);
        tag.remove(STORAGE_UNDO_MAX_Y_KEY);
        tag.remove(STORAGE_UNDO_MAX_Z_KEY);

        tag.remove(STORAGE_UNDO_BLOCKS_KEY);
        tag.remove(STORAGE_UNDO_TEMPLATE_KEY);
    }

    private static void writeUndoOrigin(CompoundTag tag, BlockPos pos) {
        tag.putInt(STORAGE_UNDO_ORIGIN_X_KEY, pos.getX());
        tag.putInt(STORAGE_UNDO_ORIGIN_Y_KEY, pos.getY());
        tag.putInt(STORAGE_UNDO_ORIGIN_Z_KEY, pos.getZ());
    }

    private static void writeUndoMin(CompoundTag tag, BlockPos pos) {
        tag.putInt(STORAGE_UNDO_MIN_X_KEY, pos.getX());
        tag.putInt(STORAGE_UNDO_MIN_Y_KEY, pos.getY());
        tag.putInt(STORAGE_UNDO_MIN_Z_KEY, pos.getZ());
    }

    private static void writeUndoMax(CompoundTag tag, BlockPos pos) {
        tag.putInt(STORAGE_UNDO_MAX_X_KEY, pos.getX());
        tag.putInt(STORAGE_UNDO_MAX_Y_KEY, pos.getY());
        tag.putInt(STORAGE_UNDO_MAX_Z_KEY, pos.getZ());
    }

    private static BlockPos readUndoOrigin(CompoundTag tag) {
        return new BlockPos(
                tag.getInt(STORAGE_UNDO_ORIGIN_X_KEY),
                tag.getInt(STORAGE_UNDO_ORIGIN_Y_KEY),
                tag.getInt(STORAGE_UNDO_ORIGIN_Z_KEY)
        );
    }

    private @Nullable CompoundTag readUndoTemplate(CompoundTag undoTag) {
        if (!undoTag.contains(STORAGE_UNDO_TEMPLATE_KEY, Tag.TAG_COMPOUND)) {
            return null;
        }

        return undoTag.getCompound(STORAGE_UNDO_TEMPLATE_KEY).copy();
    }

    private List<StorageUndoPlacedBlock> readUndoPlacedBlocks(CompoundTag undoTag) {
        if (!undoTag.contains(STORAGE_UNDO_BLOCKS_KEY, Tag.TAG_LIST)) {
            return List.of();
        }

        ListTag blocksTag = undoTag.getList(STORAGE_UNDO_BLOCKS_KEY, Tag.TAG_COMPOUND);
        List<StorageUndoPlacedBlock> out = new ArrayList<>();

        for (int i = 0; i < blocksTag.size(); i++) {
            CompoundTag blockTag = blocksTag.getCompound(i);

            BlockPos pos = new BlockPos(
                    blockTag.getInt(STORAGE_UNDO_POS_X_KEY),
                    blockTag.getInt(STORAGE_UNDO_POS_Y_KEY),
                    blockTag.getInt(STORAGE_UNDO_POS_Z_KEY)
            );

            out.add(new StorageUndoPlacedBlock(
                    pos,
                    blockTag.getString(STORAGE_UNDO_STATE_KEY)
            ));
        }

        return out;
    }

    private boolean areUndoPlacedBlocksUnchanged(
            ServerLevel level,
            List<StorageUndoPlacedBlock> undoBlocks
    ) {
        for (StorageUndoPlacedBlock undoBlock : undoBlocks) {
            BlockState currentState = level.getBlockState(undoBlock.pos());

            if (!currentState.toString().equals(undoBlock.stateSignature())) {
                return false;
            }
        }

        return true;
    }

    private void removeUndoPlacedBlocks(
            ServerLevel level,
            List<StorageUndoPlacedBlock> undoBlocks
    ) {
        BlockState air = Blocks.AIR.defaultBlockState();

        for (StorageUndoPlacedBlock undoBlock : undoBlocks) {
            level.removeBlockEntity(undoBlock.pos());
        }

        for (StorageUndoPlacedBlock undoBlock : undoBlocks) {
            if (level.getBlockState(undoBlock.pos()).isAir()) {
                continue;
            }

            level.setBlock(
                    undoBlock.pos(),
                    air,
                    STORAGE_UNDO_CLEAR_FLAGS,
                    0
            );
        }
    }

    private List<StorageUndoPlacedBlock> collectUndoPlacedBlocksAfterPaste(
            ServerLevel level,
            CompoundTag templateTag,
            BlockPos origin
    ) {
        List<StorageUndoPlacedBlock> out = new ArrayList<>();

        BlockPos energyOrigin = TemplateUtil.getEnergyOrigin(templateTag);
        BlockPos templateOffset = TemplateUtil.getTemplateOffset(templateTag);
        BlockPos placementOrigin = origin.subtract(energyOrigin).offset(templateOffset);

        for (TemplateUtil.BlockInfo blockInfo : TemplateUtil.parseRawBlocksFromTag(templateTag)) {
            BlockPos worldPos = placementOrigin.offset(blockInfo.pos());
            BlockState currentState = level.getBlockState(worldPos);

            if (currentState.isAir()) {
                continue;
            }

            out.add(new StorageUndoPlacedBlock(
                    worldPos.immutable(),
                    currentState.toString()
            ));
        }

        return out;
    }
    private @Nullable BlockBounds computeBounds(List<StorageUndoPlacedBlock> placedBlocks) {
        if (placedBlocks.isEmpty()) {
            return null;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;

        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (StorageUndoPlacedBlock placedBlock : placedBlocks) {
            BlockPos pos = placedBlock.pos();

            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());

            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        return new BlockBounds(
                new BlockPos(minX, minY, minZ),
                new BlockPos(maxX, maxY, maxZ)
        );
    }

    private void clearStoredStructure(
            ServerLevel level,
            Player player,
            ItemStack stack,
            String id
    ) {
        try {
            StructureToolStructureStore.delete(level.getServer(), id);
        } catch (IOException ignored) {
        }

        StructureToolStackState.clearStructure(stack);
        StructureToolStackState.clearSelection(stack);
        StructureToolStackState.resetPreviewSideMap(stack);

        TemplateUtil.setTemplateOffset(stack.getOrCreateTag(), BlockPos.ZERO);
        TemplateUtil.setEnergyOrigin(stack.getOrCreateTag(), BlockPos.ZERO);

        if (player instanceof ServerPlayer serverPlayer) {
            StructureToolPreviewDispatcher.sendPreviewToPlayer(serverPlayer, null);
        }
    }

    private boolean hasPlacementCollision(ServerLevel level, CompoundTag templateTag, BlockPos origin) {
        List<TemplateUtil.BlockInfo> blocks = TemplateUtil.parseRawBlocksFromTag(templateTag);

        int minBuildY = level.getMinBuildHeight();
        int maxBuildY = level.getMaxBuildHeight();

        BlockPos energyOrigin = TemplateUtil.getEnergyOrigin(templateTag);
        BlockPos templateOffset = TemplateUtil.getTemplateOffset(templateTag);
        BlockPos placementOrigin = origin.subtract(energyOrigin).offset(templateOffset);

        for (TemplateUtil.BlockInfo blockInfo : blocks) {
            BlockPos worldPos = placementOrigin.offset(blockInfo.pos());

            if (worldPos.getY() < minBuildY || worldPos.getY() >= maxBuildY) {
                return true;
            }

            BlockState existing = level.getBlockState(worldPos);

            if (existing.isAir() || existing.canBeReplaced()) {
                continue;
            }

            return true;
        }

        return false;
    }

    private record StorageUndoPlacedBlock(
            BlockPos pos,
            String stateSignature
    ) {
    }

    private record BlockBounds(
            BlockPos min,
            BlockPos max
    ) {
    }

    @Override
    protected double getEnergyCostMultiplier() {
        return SpatialConfig.COMMON.PORTABLE_SPATIAL_STORAGE_ENERGY_COST_MULTIPLIER.get();
    }
}