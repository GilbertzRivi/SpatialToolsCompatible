package net.oktawia.spatialtoolscmp.items;

import lombok.Getter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.network.NetworkHooks;
import net.oktawia.spatialtoolscmp.client.misc.StructureToolContextMenuClient;
import net.oktawia.spatialtoolscmp.defs.LangDefs;
import net.oktawia.spatialtoolscmp.items.helpers.ToolCaptureFilter;
import net.oktawia.spatialtoolscmp.items.helpers.ToolPowerManager;
import net.oktawia.spatialtoolscmp.logic.StructureCloneExtension;
import net.oktawia.spatialtoolscmp.logic.StructureToolExtensions;
import net.oktawia.spatialtoolscmp.logic.StructureToolPreviewDispatcher;
import net.oktawia.spatialtoolscmp.logic.StructureToolStackState;
import net.oktawia.spatialtoolscmp.logic.StructureToolStructureStore;
import net.oktawia.spatialtoolscmp.logic.StructureToolUtil;
import net.oktawia.spatialtoolscmp.network.NetworkHandler;
import net.oktawia.spatialtoolscmp.network.packets.ShowHudMessagePacket;
import net.oktawia.spatialtoolscmp.util.StructureToolKeys;
import net.oktawia.spatialtoolscmp.util.TemplateUtil;
import net.oktawia.spatialtoolscmp.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.function.IntSupplier;

public abstract class AbstractStructureCaptureToolItem extends Item {

    protected static final double POWER_PER_BLOCK_PASTE = 1.0D;

    private static final String WAS_HELD_IN_HAND_NBT_KEY = "wasHeldInHand";
    private static final String SELECTION_DIMENSION_NBT_KEY = "selectionDimension";

    private static final int HUD_COLOR_CYAN = 0x55FFFF;
    private static final int HUD_COLOR_RED = 0xFF4040;
    private static final int HUD_TIME_SHORT = 60;
    protected static final int HUD_TIME_MEDIUM = 100;

    private static final int CUT_CLEAR_FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private static final long DEFAULT_MAX_CAPTURE_BOUNDS_VOLUME = 1_000_000L;

    @Getter
    private final int powerUpgradeSlots;

    @Getter
    private final int maxPowerUpgrades;

    protected final ToolPowerManager powerManager;

    protected AbstractStructureCaptureToolItem(
            IntSupplier basePowerCapacitySupplier,
            int powerUpgradeSlots,
            int maxPowerUpgrades,
            Item.Properties properties
    ) {
        super(properties.stacksTo(1));

        this.powerUpgradeSlots = Math.max(0, powerUpgradeSlots);
        this.maxPowerUpgrades = Mth.clamp(maxPowerUpgrades, 0, this.powerUpgradeSlots);
        this.powerManager = new ToolPowerManager(basePowerCapacitySupplier, this.powerUpgradeSlots, this.maxPowerUpgrades);
    }

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new ToolCapabilityProvider(stack);
    }

    @FunctionalInterface
    public interface RequirementSink {
        void add(ItemStack stack);
    }

    protected record CapturedStructureResult(
            String structureId,
            CompoundTag savedTag,
            BlockPos min,
            BlockPos max,
            BlockPos origin,
            double usedPower
    ) {
    }

    protected abstract MenuType<?> getToolMenuType();

    protected abstract boolean removeCapturedBlocks();

    protected abstract Component getCaptureSuccessMessage();

    protected abstract Component getStoredStructureActionNotImplementedMessage();

    protected boolean isToolEnabled() {
        return true;
    }

    protected int getMaxStructureSize() {
        return -1;
    }

    protected long getMaxCaptureBoundsVolume() {
        int maxStructureSize = getMaxStructureSize();

        if (maxStructureSize > 0) {
            return Math.max(DEFAULT_MAX_CAPTURE_BOUNDS_VOLUME, (long) maxStructureSize * 16L);
        }

        return DEFAULT_MAX_CAPTURE_BOUNDS_VOLUME;
    }

    protected double getPowerPerBlockCapture() {
        return 1.0D;
    }

    protected double getPowerPerBlockPaste() {
        return POWER_PER_BLOCK_PASTE;
    }

    protected double getEnergyCostMultiplier() {
        return 1.0D;
    }

    protected void afterStructureCaptured(
            ServerLevel level,
            Player player,
            ItemStack stack,
            CapturedStructureResult result
    ) {
    }

    protected boolean tryUsePower(Player player, ItemStack stack, double amount) {
        return powerManager.tryUse(player, stack, amount);
    }

    protected int getInternalPowerCapacity(ItemStack stack) {
        return powerManager.getCapacity(stack);
    }

    protected int getInternalPowerStored(ItemStack stack) {
        return powerManager.getStored(stack);
    }

    protected int extractInternalPower(ItemStack stack, double amount, boolean simulate) {
        return powerManager.extract(stack, amount, simulate);
    }

    protected int receiveInternalPower(ItemStack stack, double amount, boolean simulate) {
        return powerManager.receive(stack, amount, simulate);
    }

    protected int getInstalledPowerUpgrades(ItemStack stack) {
        return powerManager.getInstalledUpgrades(stack);
    }

    public static boolean isValidPowerUpgradeItem(ItemStack stack) {
        return ToolPowerManager.isValidPowerUpgradeItem(stack);
    }

    public static List<ResourceLocation> getConfiguredPowerUpgradeItemIds() {
        return ToolPowerManager.getConfiguredPowerUpgradeItemIds();
    }

    public static List<ItemStack> getConfiguredPowerUpgradeItemStacks() {
        return ToolPowerManager.getConfiguredPowerUpgradeItemStacks();
    }

    public static boolean isValidCraftingUpgradeItem(ItemStack stack) {
        return ToolPowerManager.isValidCraftingUpgradeItem(stack);
    }

    public int getCraftingUpgradeSlotIndex() {
        return powerManager.getCraftingUpgradeSlotIndex();
    }

    public boolean hasCraftingUpgradeSlot() {
        return powerManager.hasCraftingUpgradeSlot();
    }

    public boolean isCraftingUpgradeSlot(int slot) {
        return powerManager.isCraftingUpgradeSlot(slot);
    }

    public boolean hasInstalledCraftingUpgrade(ItemStack stack) {
        return powerManager.hasInstalledCraftingUpgrade(stack);
    }

    protected void showNotEnoughPower(Player player, ItemStack stack, double required) {
        int current = powerManager.getStored(stack);
        int needed = (int) Math.ceil(required);

        showHud(
                player,
                HUD_TIME_MEDIUM,
                red(Component.translatable(LangDefs.NOT_ENOUGH_POWER.getTranslationKey())),
                cyan(Component.translatable(LangDefs.NEED_FE.getTranslationKey(), needed)),
                cyan(Component.translatable(LangDefs.HAVE_FE.getTranslationKey(), current))
        );
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);

        powerManager.clamp(stack);

        if (level.isClientSide() || !(entity instanceof Player player)) {
            return;
        }

        boolean isHeldNow = player.getMainHandItem() == stack || player.getOffhandItem() == stack;
        CompoundTag tag = stack.getOrCreateTag();
        boolean wasHeldBefore = tag.getBoolean(WAS_HELD_IN_HAND_NBT_KEY);

        if (isHeldNow && !wasHeldBefore) {
            showHud(player, Component.translatable(LangDefs.CORNER_0_SELECTED.getTranslationKey()));
        }

        tag.putBoolean(WAS_HELD_IN_HAND_NBT_KEY, isHeldNow);

        if (isHeldNow) {
            ensureSelectionDimensionOrClear(level, player, stack, true);
        }
    }

    protected static ShowHudMessagePacket.Line cyan(Component text) {
        return new ShowHudMessagePacket.Line(text, HUD_COLOR_CYAN);
    }

    protected static ShowHudMessagePacket.Line red(Component text) {
        return new ShowHudMessagePacket.Line(text, HUD_COLOR_RED);
    }

    protected void showHud(Player player, int durationTicks, ShowHudMessagePacket.Line... lines) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        NetworkHandler.sendToPlayer(serverPlayer, new ShowHudMessagePacket(durationTicks, List.of(lines)));
    }

    protected void showHud(Player player, Component text) {
        showHud(player, HUD_TIME_SHORT, cyan(text));
    }

    protected boolean checkStructureSizeLimit(Player player, CompoundTag templateTag) {
        int maxSize = getMaxStructureSize();

        if (maxSize < 0) {
            return true;
        }

        int blockCount = TemplateUtil.parseRawBlocksFromTag(templateTag).size();

        if (blockCount <= maxSize) {
            return true;
        }

        showHud(
                player,
                HUD_TIME_MEDIUM,
                red(Component.translatable(LangDefs.STRUCTURE_TOO_LARGE.getTranslationKey())),
                cyan(Component.translatable(LangDefs.STRUCTURE_SIZE.getTranslationKey(), blockCount)),
                cyan(Component.translatable(LangDefs.STRUCTURE_SIZE_LIMIT.getTranslationKey(), maxSize))
        );

        return false;
    }

    protected static boolean isTemplateEmpty(CompoundTag templateTag) {
        return TemplateUtil.parseRawBlocksFromTag(templateTag).isEmpty();
    }

    protected static boolean shouldSkipStructureToolBlock(Level level, BlockPos pos, BlockState state) {
        return ToolCaptureFilter.shouldSkipStructureToolBlock(level, pos, state);
    }

    protected static CompoundTag filterUncapturableBlocksFromTemplate(
            Level level,
            BlockPos worldOrigin,
            CompoundTag templateTag
    ) {
        return ToolCaptureFilter.filterUncapturableBlocksFromTemplate(level, worldOrigin, templateTag);
    }

    private boolean ensureSelectionDimensionOrClear(
            Level level,
            Player player,
            ItemStack stack,
            boolean notify
    ) {
        if (StructureToolStackState.hasStructure(stack)) {
            clearSelectionDimension(stack);
            return true;
        }

        BlockPos selectionA = StructureToolStackState.getSelectionA(stack);

        if (selectionA == null) {
            clearSelectionDimension(stack);
            return true;
        }

        String currentDimension = level.dimension().location().toString();
        CompoundTag tag = stack.getOrCreateTag();

        if (!tag.contains(SELECTION_DIMENSION_NBT_KEY, Tag.TAG_STRING)) {
            tag.putString(SELECTION_DIMENSION_NBT_KEY, currentDimension);
            return true;
        }

        String selectionDimension = tag.getString(SELECTION_DIMENSION_NBT_KEY);

        if (currentDimension.equals(selectionDimension)) {
            return true;
        }

        clearSelectionState(stack);

        if (player instanceof ServerPlayer serverPlayer) {
            StructureToolPreviewDispatcher.sendPreviewToPlayer(serverPlayer, null);
        }

        if (notify) {
            showHud(
                    player,
                    HUD_TIME_MEDIUM,
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_SELECTION_CLEARED.getTranslationKey())),
                    cyan(Component.translatable(LangDefs.STRUCTURE_GADGET_DIMENSION_CHANGED.getTranslationKey()))
            );
        }

        return false;
    }

    private static void clearSelectionDimension(ItemStack stack) {
        CompoundTag tag = stack.getTag();

        if (tag != null) {
            tag.remove(SELECTION_DIMENSION_NBT_KEY);
        }
    }

    private static void rememberSelectionDimension(ItemStack stack, Level level) {
        stack.getOrCreateTag().putString(
                SELECTION_DIMENSION_NBT_KEY,
                level.dimension().location().toString()
        );
    }

    protected static void clearSelectionState(ItemStack stack) {
        StructureToolStackState.clearSelection(stack);
        StructureToolStackState.resetPreviewSideMap(stack);
        clearSelectionDimension(stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!isToolEnabled()) {
            return InteractionResultHolder.success(stack);
        }

        if (!level.isClientSide()) {
            if (!ensureSelectionDimensionOrClear(level, player, stack, true)) {
                return InteractionResultHolder.success(stack);
            }

            boolean hasStructure = StructureToolStackState.hasStructure(stack);
            boolean blockInFrontMode = StructureToolStackState.isBlockInFrontSelectionMode(stack);
            boolean completeSelection = hasCompleteSelection(stack);

            if (player.isShiftKeyDown()) {
                if (!hasStructure && blockInFrontMode && !completeSelection) {
                    selectNextCornerAt(
                            (ServerLevel) level,
                            player,
                            stack,
                            StructureToolStackState.getBlockInFrontSelectionPos(player).immutable()
                    );

                    return InteractionResultHolder.success(stack);
                }

                openMenu(player, hand);
                return InteractionResultHolder.success(stack);
            }

            if (hasStructure) {
                onUseWithStoredStructure((ServerLevel) level, player, stack);
                return InteractionResultHolder.success(stack);
            }

            if (blockInFrontMode) {
                if (completeSelection) {
                    captureStructure((ServerLevel) level, player, stack);
                } else {
                    selectNextCornerAt(
                            (ServerLevel) level,
                            player,
                            stack,
                            StructureToolStackState.getBlockInFrontSelectionPos(player).immutable()
                    );
                }

                return InteractionResultHolder.success(stack);
            }

            if (isWaitingForSecondCorner(stack)) {
                selectSecondCorner((ServerLevel) level, player, stack);
                return InteractionResultHolder.success(stack);
            }

            captureStructure((ServerLevel) level, player, stack);
        }

        return InteractionResultHolder.success(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();

        if (!isToolEnabled()) {
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        Player player = context.getPlayer();

        if (player == null) {
            return InteractionResult.PASS;
        }

        ItemStack stack = context.getItemInHand();
        BlockPos clickedPos = context.getClickedPos();
        boolean hasStructure = StructureToolStackState.hasStructure(stack);
        boolean blockInFrontMode = StructureToolStackState.isBlockInFrontSelectionMode(stack);
        boolean completeSelection = hasCompleteSelection(stack);

        if (!level.isClientSide() && !ensureSelectionDimensionOrClear(level, player, stack, true)) {
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        if (player.isShiftKeyDown()) {
            if (hasStructure) {
                if (!level.isClientSide()) {
                    openMenu(player, context.getHand());
                }

                return InteractionResult.sidedSuccess(level.isClientSide());
            }

            BlockPos selectedPos = StructureToolStackState.resolveSelectionPos(
                    stack,
                    player,
                    clickedPos
            ).immutable();

            if (!level.isClientSide()) {
                selectNextCornerAt(
                        (ServerLevel) level,
                        player,
                        stack,
                        selectedPos
                );
            }

            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        if (hasStructure) {
            if (!level.isClientSide()) {
                onUseOnWithStoredStructure(
                        (ServerLevel) level,
                        player,
                        stack,
                        clickedPos.relative(context.getClickedFace())
                );
            }

            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        if (blockInFrontMode) {
            if (!completeSelection && !level.isClientSide()) {
                selectNextCornerAt(
                        (ServerLevel) level,
                        player,
                        stack,
                        StructureToolStackState.getBlockInFrontSelectionPos(player).immutable()
                );
            }

            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        if (!level.isClientSide() && isWaitingForSecondCorner(stack)) {
            selectSecondCorner((ServerLevel) level, player, stack);
        }

        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    protected static boolean hasCompleteSelection(ItemStack stack) {
        return !StructureToolStackState.hasStructure(stack)
                && StructureToolStackState.getSelectionA(stack) != null
                && StructureToolStackState.getSelectionB(stack) != null;
    }

    protected void selectNextCornerAt(ServerLevel level, Player player, ItemStack stack, BlockPos pos) {
        if (!ensureSelectionDimensionOrClear(level, player, stack, true)) {
            return;
        }

        BlockPos selectionA = StructureToolStackState.getSelectionA(stack);
        BlockPos selectionB = StructureToolStackState.getSelectionB(stack);

        if (selectionA == null) {
            StructureToolStackState.setSelectionA(stack, pos.immutable());
            rememberSelectionDimension(stack, level);
            showHud(player, Component.translatable(LangDefs.CORNER_A_SELECTED.getTranslationKey()));
            return;
        }

        if (selectionB == null) {
            StructureToolStackState.setSelectionB(stack, pos.immutable());
            StructureToolStackState.setSourceFacing(stack, player.getDirection());
            showHud(player, Component.translatable(LangDefs.CORNER_B_SELECTED.getTranslationKey()));
            return;
        }

        clearSelectionState(stack);
        showHud(player, Component.translatable(LangDefs.SELECTION_RESTARTED.getTranslationKey()));
    }

    protected void onUseWithStoredStructure(ServerLevel level, Player player, ItemStack stack) {
        showHud(player, getStoredStructureActionNotImplementedMessage());
    }

    protected void onUseOnWithStoredStructure(ServerLevel level, Player player, ItemStack stack, BlockPos clickedFacePos) {
        showHud(player, getStoredStructureActionNotImplementedMessage());
    }

    protected static boolean isWaitingForSecondCorner(ItemStack stack) {
        return !StructureToolStackState.hasStructure(stack)
                && StructureToolStackState.getSelectionA(stack) != null
                && StructureToolStackState.getSelectionB(stack) == null;
    }

    protected void openMenu(Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        ItemStack stack = player.getItemInHand(hand);

        MenuProvider provider = new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return stack.getHoverName();
            }

            @Nullable
            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player menuPlayer) {
                return createToolMenu(containerId, inventory, menuPlayer, hand);
            }
        };

        NetworkHooks.openScreen(serverPlayer, provider);
    }

    @Nullable
    protected AbstractContainerMenu createToolMenu(
            int containerId,
            Inventory inventory,
            Player player,
            InteractionHand hand
    ) {
        return getToolMenuType().create(containerId, inventory);
    }

    protected void selectSecondCorner(ServerLevel level, Player player, ItemStack stack) {
        if (!ensureSelectionDimensionOrClear(level, player, stack, true)) {
            return;
        }

        BlockPos pos;

        if (StructureToolStackState.isBlockInFrontSelectionMode(stack)) {
            pos = StructureToolStackState.getBlockInFrontSelectionPos(player).immutable();
        } else {
            BlockHitResult hit = StructureToolUtil.rayTrace(level, player, 50.0D);

            if (hit.getType() != HitResult.Type.BLOCK) {
                showHud(player, Component.translatable(LangDefs.NO_BLOCK_IN_RANGE.getTranslationKey()));
                return;
            }

            pos = hit.getBlockPos().immutable();
        }

        StructureToolStackState.setSelectionB(stack, pos);
        StructureToolStackState.setSourceFacing(stack, player.getDirection());

        showHud(player, Component.translatable(LangDefs.CORNER_B_SELECTED.getTranslationKey()));
    }

    protected void captureStructure(ServerLevel level, Player player, ItemStack stack) {
        BlockPos a = StructureToolStackState.getSelectionA(stack);
        BlockPos b = StructureToolStackState.getSelectionB(stack);

        if (a == null || b == null) {
            return;
        }

        if (!ensureSelectionDimensionOrClear(level, player, stack, true)) {
            return;
        }

        BlockPos min = new BlockPos(
                Math.min(a.getX(), b.getX()),
                Math.min(a.getY(), b.getY()),
                Math.min(a.getZ(), b.getZ())
        );

        BlockPos max = new BlockPos(
                Math.max(a.getX(), b.getX()),
                Math.max(a.getY(), b.getY()),
                Math.max(a.getZ(), b.getZ())
        );

        captureStructureFromBounds(
                level,
                player,
                stack,
                min,
                max,
                b,
                true,
                removeCapturedBlocks(),
                !player.isCreative(),
                true
        );
    }

    protected @Nullable CapturedStructureResult captureStructureFromBounds(
            ServerLevel level,
            Player player,
            ItemStack stack,
            BlockPos min,
            BlockPos max,
            BlockPos origin,
            boolean consumePower,
            boolean removeBlocks,
            boolean filterUncapturable,
            boolean showSuccess
    ) {
        if (!preflightCaptureBounds(level, player, stack, min, max, consumePower, filterUncapturable)) {
            return null;
        }

        long sizeX = boundsSizeX(min, max);
        long sizeY = boundsSizeY(min, max);
        long sizeZ = boundsSizeZ(min, max);

        BlockPos size = new BlockPos((int) sizeX, (int) sizeY, (int) sizeZ);

        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, min, size, false, Blocks.STRUCTURE_VOID);

        CompoundTag savedTag = template.save(new CompoundTag());

        if (filterUncapturable) {
            savedTag = filterUncapturableBlocksFromTemplate(level, min, savedTag);
        }

        savedTag = TemplateUtil.stripAirFromTag(savedTag);
        TemplateUtil.setTemplateOffset(savedTag, BlockPos.ZERO);

        if (isTemplateEmpty(savedTag)) {
            clearSelectionState(stack);

            TemplateUtil.setTemplateOffset(stack.getOrCreateTag(), BlockPos.ZERO);
            TemplateUtil.setEnergyOrigin(stack.getOrCreateTag(), BlockPos.ZERO);

            if (player instanceof ServerPlayer serverPlayer) {
                StructureToolPreviewDispatcher.sendPreviewToPlayer(serverPlayer, null);
            }

            showHud(
                    player,
                    HUD_TIME_MEDIUM,
                    red(Component.translatable(LangDefs.STRUCTURE_GADGET_SELECTION_EMPTY_OR_SKIPPED.getTranslationKey())),
                    cyan(Component.translatable(LangDefs.STRUCTURE_GADGET_NOTHING_CAPTURED.getTranslationKey()))
            );

            return null;
        }

        if (!checkStructureSizeLimit(player, savedTag)) {
            return null;
        }

        CompoundTag metadata = getMetadata(level, player, min, max, savedTag);

        if (!metadata.isEmpty()) {
            savedTag.put(StructureToolKeys.CLONE_METADATA_KEY, metadata);
        }

        BlockPos localOrigin = origin.subtract(min);
        TemplateUtil.setEnergyOrigin(savedTag, localOrigin);
        TemplateUtil.copyPreviewTransformState(savedTag, stack.getOrCreateTag());

        double requiredPower = StructureToolUtil.calculatePreviewStructurePower(
                savedTag,
                localOrigin,
                getPowerPerBlockCapture(),
                getEnergyCostMultiplier()
        );

        double usedPower = 0.0D;

        if (consumePower && !player.isCreative()) {
            if (!tryUsePower(player, stack, requiredPower)) {
                showNotEnoughPower(player, stack, requiredPower);
                return null;
            }

            usedPower = Math.ceil(requiredPower);
        }

        String id;

        try {
            id = saveCapturedStructure(level, player, stack, savedTag);
            StructureToolStackState.setStructureId(stack, id);
            clearSelectionState(stack);
        } catch (IOException exception) {
            showHud(player, Component.translatable(LangDefs.FAILED_TO_SAVE_STRUCTURE.getTranslationKey()));
            return null;
        }

        CapturedStructureResult result = new CapturedStructureResult(id, savedTag, min, max, origin, usedPower);

        afterStructureCaptured(level, player, stack, result);

        if (removeBlocks) {
            removeCapturedBlocksWithoutDrops(level, min, savedTag, filterUncapturable);
        }

        if (player instanceof ServerPlayer serverPlayer) {
            StructureToolPreviewDispatcher.sendPreviewToPlayer(serverPlayer, savedTag);
        }

        if (showSuccess) {
            if (removeCapturedBlocks()) {
                showHud(
                        player,
                        HUD_TIME_MEDIUM,
                        cyan(getCaptureSuccessMessage()),
                        cyan(Component.translatable(LangDefs.STRUCTURE_GADGET_UNDO_HINT.getTranslationKey()))
                );
            } else {
                showHud(player, getCaptureSuccessMessage());
            }
        }

        return result;
    }

    private boolean preflightCaptureBounds(
            ServerLevel level,
            Player player,
            ItemStack stack,
            BlockPos min,
            BlockPos max,
            boolean consumePower,
            boolean filterUncapturable
    ) {
        long sizeX = boundsSizeX(min, max);
        long sizeY = boundsSizeY(min, max);
        long sizeZ = boundsSizeZ(min, max);

        if (sizeX <= 0L || sizeY <= 0L || sizeZ <= 0L) {
            showHud(
                    player,
                    HUD_TIME_MEDIUM,
                    red(Component.translatable(LangDefs.STRUCTURE_TOO_LARGE.getTranslationKey()))
            );
            return false;
        }

        if (sizeX > Integer.MAX_VALUE || sizeY > Integer.MAX_VALUE || sizeZ > Integer.MAX_VALUE) {
            showHud(
                    player,
                    HUD_TIME_MEDIUM,
                    red(Component.translatable(LangDefs.STRUCTURE_TOO_LARGE.getTranslationKey()))
            );
            return false;
        }

        long volume = safeVolume(sizeX, sizeY, sizeZ);
        long maxVolume = getMaxCaptureBoundsVolume();

        if (maxVolume >= 0L && volume > maxVolume) {
            showHud(
                    player,
                    HUD_TIME_MEDIUM,
                    red(Component.translatable(LangDefs.STRUCTURE_TOO_LARGE.getTranslationKey())),
                    cyan(Component.translatable(LangDefs.STRUCTURE_SIZE.getTranslationKey(), volume)),
                    cyan(Component.translatable(LangDefs.STRUCTURE_SIZE_LIMIT.getTranslationKey(), maxVolume))
            );
            return false;
        }

        int maxStructureSize = getMaxStructureSize();
        boolean shouldCheckStructureSize = maxStructureSize >= 0;

        boolean shouldCheckPower = consumePower
                && !player.isCreative()
                && getPowerPerBlockCapture() > 0.0D
                && getEnergyCostMultiplier() > 0.0D;

        if (!shouldCheckStructureSize && !shouldCheckPower) {
            return true;
        }

        long countedBlocks = 0L;
        double estimatedRequiredPower = 0.0D;
        double estimatedPowerPerBlock = getPowerPerBlockCapture() * getEnergyCostMultiplier();
        int storedPower = getInternalPowerStored(stack);

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int x = min.getX(); x <= max.getX(); x++) {
                    mutablePos.set(x, y, z);

                    BlockState state = level.getBlockState(mutablePos);

                    if (state.isAir()) {
                        continue;
                    }

                    if (filterUncapturable && shouldSkipStructureToolBlock(level, mutablePos, state)) {
                        continue;
                    }

                    countedBlocks++;

                    if (shouldCheckStructureSize && countedBlocks > maxStructureSize) {
                        showHud(
                                player,
                                HUD_TIME_MEDIUM,
                                red(Component.translatable(LangDefs.STRUCTURE_TOO_LARGE.getTranslationKey())),
                                cyan(Component.translatable(LangDefs.STRUCTURE_SIZE.getTranslationKey(), countedBlocks)),
                                cyan(Component.translatable(LangDefs.STRUCTURE_SIZE_LIMIT.getTranslationKey(), maxStructureSize))
                        );
                        return false;
                    }

                    if (shouldCheckPower) {
                        estimatedRequiredPower += estimatedPowerPerBlock;

                        if (Math.ceil(estimatedRequiredPower) > storedPower) {
                            showNotEnoughPower(player, stack, estimatedRequiredPower);
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }

    private static long boundsSizeX(BlockPos min, BlockPos max) {
        return (long) max.getX() - min.getX() + 1L;
    }

    private static long boundsSizeY(BlockPos min, BlockPos max) {
        return (long) max.getY() - min.getY() + 1L;
    }

    private static long boundsSizeZ(BlockPos min, BlockPos max) {
        return (long) max.getZ() - min.getZ() + 1L;
    }

    private static long safeVolume(long x, long y, long z) {
        if (x <= 0L || y <= 0L || z <= 0L) {
            return Long.MAX_VALUE;
        }

        if (x > Long.MAX_VALUE / y) {
            return Long.MAX_VALUE;
        }

        long xy = x * y;

        if (xy > Long.MAX_VALUE / z) {
            return Long.MAX_VALUE;
        }

        return xy * z;
    }

    private static void removeCapturedBlocksWithoutDrops(
            ServerLevel level,
            BlockPos min,
            CompoundTag savedTag,
            boolean skipUncapturable
    ) {
        BlockState air = Blocks.AIR.defaultBlockState();
        List<TemplateUtil.BlockInfo> blocksToRemove = TemplateUtil.parseRawBlocksFromTag(savedTag);

        for (TemplateUtil.BlockInfo info : blocksToRemove) {
            BlockPos worldPos = min.offset(info.pos());

            if (skipUncapturable && shouldSkipStructureToolBlock(level, worldPos, level.getBlockState(worldPos))) {
                continue;
            }

            level.removeBlockEntity(worldPos);
        }

        for (TemplateUtil.BlockInfo info : blocksToRemove) {
            BlockPos worldPos = min.offset(info.pos());
            BlockState currentState = level.getBlockState(worldPos);

            if (currentState.isAir()) {
                continue;
            }

            if (skipUncapturable && shouldSkipStructureToolBlock(level, worldPos, currentState)) {
                continue;
            }

            level.setBlock(worldPos, air, CUT_CLEAR_FLAGS, 0);
        }
    }

    private CompoundTag getMetadata(ServerLevel level, Player player, BlockPos min, BlockPos max, CompoundTag savedTag) {
        CompoundTag data = new CompoundTag();
        ListTag blocks = new ListTag();
        RequirementAccumulator requirements = new RequirementAccumulator();

        Map<BlockPos, CompoundTag> rawBeTags = new LinkedHashMap<>();

        for (TemplateUtil.BlockInfo info : TemplateUtil.parseRawBlocksFromTag(savedTag)) {
            if (info.blockEntityTag() != null) {
                rawBeTags.put(info.pos(), info.blockEntityTag());
            }
        }

        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int x = min.getX(); x <= max.getX(); x++) {
                    BlockPos worldPos = new BlockPos(x, y, z);
                    BlockPos localPos = worldPos.subtract(min);

                    BlockState state = level.getBlockState(worldPos);

                    if (!player.isCreative() && shouldSkipStructureToolBlock(level, worldPos, state)) {
                        continue;
                    }

                    BlockEntity be = level.getBlockEntity(worldPos);
                    CompoundTag rawBeTag = rawBeTags.get(localPos);

                    boolean requirementsHandled = false;

                    for (StructureCloneExtension extension : StructureToolExtensions.clonerExtensions()) {
                        if (extension.handlesRequirements(state, rawBeTag)) {
                            requirementsHandled = true;
                            break;
                        }
                    }

                    if (!requirementsHandled || be == null) {
                        addBaseBlockRequirement(level, worldPos, requirements);
                    }

                    if (be == null) {
                        continue;
                    }

                    CompoundTag blockEntry = new CompoundTag();
                    blockEntry.put(StructureToolKeys.CLONE_KEY_POS, writeBlockPos(localPos));

                    boolean hasAnyData = false;

                    for (StructureCloneExtension extension : StructureToolExtensions.clonerExtensions()) {
                        if (extension.collectMetadata(
                                level,
                                worldPos,
                                be,
                                rawBeTag,
                                requirements::add,
                                blockEntry
                        )) {
                            hasAnyData = true;
                        }
                    }

                    if (hasAnyData) {
                        blocks.add(blockEntry);
                    }
                }
            }
        }

        if (!blocks.isEmpty()) {
            data.put(StructureToolKeys.CLONE_METADATA_BLOCKS_KEY, blocks);
        }

        ListTag requirementList = requirements.toListTag();

        if (!requirementList.isEmpty()) {
            data.put(StructureToolKeys.CLONE_REQUIREMENTS_KEY, requirementList);
        }

        return data;
    }

    private static void addBaseBlockRequirement(ServerLevel level, BlockPos pos, RequirementAccumulator requirements) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        ItemStack picked = level.getBlockState(pos).getCloneItemStack(hit, level, pos, null);

        if (!picked.isEmpty()) {
            requirements.addDefault(picked);
            return;
        }

        Item item = level.getBlockState(pos).getBlock().asItem();

        if (item != Items.AIR) {
            requirements.addDefault(new ItemStack(item));
        }
    }

    private static CompoundTag writeBlockPos(BlockPos pos) {
        CompoundTag tag = new CompoundTag();

        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());

        return tag;
    }

    private enum RequirementKind {
        DEFAULT
    }

    private static final class RequirementAccumulator {
        private final Map<Item, RequirementEntry> entries = new LinkedHashMap<>();

        private void add(ItemStack stack, RequirementKind kind) {
            ItemStack normalized = normalize(stack);

            if (normalized.isEmpty()) {
                return;
            }

            int amount = Math.max(1, stack.getCount());
            RequirementEntry existing = entries.get(normalized.getItem());

            if (existing == null) {
                entries.put(normalized.getItem(), new RequirementEntry(normalized, amount));
            } else {
                existing.count += amount;
            }
        }

        private void add(ItemStack stack) {
            add(stack, RequirementKind.DEFAULT);
        }

        private void addDefault(ItemStack stack) {
            add(stack, RequirementKind.DEFAULT);
        }

        private ListTag toListTag() {
            ListTag list = new ListTag();

            for (RequirementEntry entry : entries.values()) {
                CompoundTag row = new CompoundTag();

                row.put(StructureToolKeys.CLONE_KEY_STACK, entry.stack.save(new CompoundTag()));
                row.putInt(StructureToolKeys.CLONE_KEY_COUNT, entry.count);

                list.add(row);
            }

            return list;
        }

        private static ItemStack normalize(ItemStack stack) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }

            ItemStack copy = stack.copy();
            copy.setCount(1);
            copy.setTag(null);

            return copy;
        }
    }

    private static final class RequirementEntry {
        private final ItemStack stack;
        private int count;

        private RequirementEntry(ItemStack stack, int count) {
            this.stack = stack;
            this.count = count;
        }
    }

    // --- Structure save / tooltip / bar ---

    protected String saveCapturedStructure(
            ServerLevel level,
            Player player,
            ItemStack stack,
            CompoundTag savedTag
    ) throws IOException {
        String id = UUID.randomUUID().toString();
        StructureToolStructureStore.save(level.getServer(), id, savedTag);
        return id;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int stored = getInternalPowerStored(stack);
        int capacity = getInternalPowerCapacity(stack);

        tooltip.add(Component.translatable(LangDefs.HOTKEY_TOOLTIP.getTranslationKey())
                .copy().withStyle(ChatFormatting.GRAY)
                .append(StructureToolContextMenuClient.OPEN_CONTEXT_MENU.getKey().getDisplayName()
                        .copy().withStyle(ChatFormatting.AQUA)
                ));

        tooltip.add(Component.translatable(
                LangDefs.STRUCTURE_TOOL_HOLD_TO_OPEN.getTranslationKey()
        ).withStyle(ChatFormatting.GRAY));

        tooltip.add(Component.translatable(
                LangDefs.STRUCTURE_TOOL_STORED_ENERGY.getTranslationKey(),
                energyValueComponent(stored),
                energyValueComponent(capacity),
                energyPercentComponent(stored, capacity)
        ).withStyle(ChatFormatting.GRAY));
    }

    private static Component energyValueComponent(int value) {
        return Component.translatable(
                LangDefs.STRUCTURE_TOOL_ENERGY_VALUE.getTranslationKey(),
                Utils.shortenNumber(value, 1)
        ).withStyle(ChatFormatting.AQUA);
    }

    private static Component energyPercentComponent(int stored, int capacity) {
        int percent = capacity <= 0 ? 0 : Mth.clamp(
                Math.round(stored * 100.0F / capacity),
                0,
                100
        );

        ChatFormatting color = percent >= 100
                ? ChatFormatting.GREEN
                : percent <= 10
                  ? ChatFormatting.RED
                  : ChatFormatting.YELLOW;

        return Component.translatable(
                LangDefs.STRUCTURE_TOOL_ENERGY_PERCENT.getTranslationKey(),
                percent
        ).withStyle(color);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getInternalPowerCapacity(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int capacity = getInternalPowerCapacity(stack);

        if (capacity <= 0) {
            return 0;
        }

        int stored = getInternalPowerStored(stack);
        return Mth.clamp(Math.round(13.0F * stored / capacity), 0, 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        int capacity = getInternalPowerCapacity(stack);

        if (capacity <= 0) {
            return 0x555555;
        }

        float ratio = Mth.clamp(getInternalPowerStored(stack) / (float) capacity, 0.0F, 1.0F);

        return Mth.hsvToRgb(ratio / 3.0F, 1.0F, 1.0F);
    }

    // --- Capability provider ---

    private final class ToolCapabilityProvider implements ICapabilityProvider {

        private final LazyOptional<IEnergyStorage> energy;
        private final LazyOptional<IItemHandler> itemHandler;

        private ToolCapabilityProvider(ItemStack stack) {
            this.energy = LazyOptional.of(() -> new StackEnergyStorage(stack));
            this.itemHandler = LazyOptional.of(() -> new PowerUpgradeItemHandler(stack, powerUpgradeSlots));
        }

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(
                @NotNull Capability<T> capability,
                @Nullable Direction side
        ) {
            if (capability == ForgeCapabilities.ENERGY) {
                return this.energy.cast();
            }

            if (capability == ForgeCapabilities.ITEM_HANDLER) {
                return this.itemHandler.cast();
            }

            return LazyOptional.empty();
        }
    }

    private final class StackEnergyStorage implements IEnergyStorage {

        private final ItemStack stack;

        private StackEnergyStorage(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return receiveInternalPower(this.stack, maxReceive, simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return getInternalPowerStored(this.stack);
        }

        @Override
        public int getMaxEnergyStored() {
            return getInternalPowerCapacity(this.stack);
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return getMaxEnergyStored() > 0;
        }
    }

    private final class PowerUpgradeItemHandler extends ItemStackHandler {

        private final ItemStack containerStack;
        private boolean loading;

        private PowerUpgradeItemHandler(ItemStack containerStack, int slots) {
            super(slots);

            this.containerStack = containerStack;
            loadFromStack();
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected int getStackLimit(int slot, @NotNull ItemStack stack) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (slot < 0 || slot >= powerUpgradeSlots || stack.isEmpty()) {
                return false;
            }

            if (slot < maxPowerUpgrades) {
                return isValidPowerUpgradeItem(stack);
            }

            return isCraftingUpgradeSlot(slot) && isValidCraftingUpgradeItem(stack);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (!isItemValid(slot, stack)) {
                return stack;
            }

            return super.insertItem(slot, stack, simulate);
        }

        @Override
        protected void onContentsChanged(int slot) {
            if (this.loading) {
                return;
            }

            sanitize();
            saveToStack();
            powerManager.clamp(this.containerStack);
        }

        private void loadFromStack() {
            this.loading = true;

            try {
                for (int slot = 0; slot < getSlots(); slot++) {
                    this.stacks.set(slot, ItemStack.EMPTY);
                }

                CompoundTag tag = this.containerStack.getTag();

                if (tag == null || !tag.contains(ToolPowerManager.POWER_UPGRADES_NBT_KEY, Tag.TAG_COMPOUND)) {
                    return;
                }

                CompoundTag inventoryTag = tag.getCompound(ToolPowerManager.POWER_UPGRADES_NBT_KEY);

                if (!inventoryTag.contains("Items", Tag.TAG_LIST)) {
                    return;
                }

                ListTag items = inventoryTag.getList("Items", Tag.TAG_COMPOUND);

                for (int i = 0; i < items.size(); i++) {
                    CompoundTag row = items.getCompound(i);
                    int slot = row.getInt("Slot");

                    if (slot < 0 || slot >= getSlots()) {
                        continue;
                    }

                    if (!row.contains("Stack", Tag.TAG_COMPOUND)) {
                        continue;
                    }

                    ItemStack stack = ItemStack.of(row.getCompound("Stack"));

                    if (!isItemValid(slot, stack)) {
                        continue;
                    }

                    stack = stack.copy();
                    stack.setCount(1);
                    this.stacks.set(slot, stack);
                }
            } finally {
                this.loading = false;
            }

            sanitize();
        }

        private void saveToStack() {
            CompoundTag inventoryTag = new CompoundTag();
            ListTag items = new ListTag();

            for (int slot = 0; slot < getSlots(); slot++) {
                ItemStack stack = getStackInSlot(slot);

                if (stack.isEmpty()) {
                    continue;
                }

                if (!isItemValid(slot, stack)) {
                    continue;
                }

                ItemStack stored = stack.copy();
                stored.setCount(1);

                CompoundTag row = new CompoundTag();
                row.putInt("Slot", slot);
                row.put("Stack", stored.save(new CompoundTag()));

                items.add(row);
            }

            inventoryTag.put("Items", items);
            this.containerStack.getOrCreateTag().put(ToolPowerManager.POWER_UPGRADES_NBT_KEY, inventoryTag);
        }

        private void sanitize() {
            for (int slot = 0; slot < getSlots(); slot++) {
                ItemStack stack = this.stacks.get(slot);

                if (stack.isEmpty()) {
                    continue;
                }

                if (!isItemValid(slot, stack)) {
                    this.stacks.set(slot, ItemStack.EMPTY);
                    continue;
                }

                if (stack.getCount() != 1) {
                    ItemStack copy = stack.copy();
                    copy.setCount(1);
                    this.stacks.set(slot, copy);
                }
            }
        }
    }
}