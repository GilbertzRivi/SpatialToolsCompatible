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
import net.minecraftforge.registries.ForgeRegistries;
import net.oktawia.spatialtoolscmp.IsModLoaded;
import net.oktawia.spatialtoolscmp.SpatialConfig;
import net.oktawia.spatialtoolscmp.compat.ae2.AE2Compat;
import net.oktawia.spatialtoolscmp.defs.LangDefs;
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

    protected static final String CURRENT_POWER_NBT_KEY = "internalCurrentPower";
    protected static final String POWER_UPGRADES_NBT_KEY = "internalPowerUpgradeInventory";

    private static final String WAS_HELD_IN_HAND_NBT_KEY = "wasHeldInHand";
    private static final String SELECTION_DIMENSION_NBT_KEY = "selectionDimension";

    private static final int HUD_COLOR_CYAN = 0x55FFFF;
    private static final int HUD_COLOR_RED = 0xFF4040;
    private static final int HUD_TIME_SHORT = 60;
    protected static final int HUD_TIME_MEDIUM = 100;

    private static final int CUT_CLEAR_FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private final IntSupplier basePowerCapacitySupplier;

    @Getter
    private final int powerUpgradeSlots;

    @Getter
    private final int maxPowerUpgrades;

    protected AbstractStructureCaptureToolItem(
            IntSupplier basePowerCapacitySupplier,
            int powerUpgradeSlots,
            int maxPowerUpgrades,
            Item.Properties properties
    ) {
        super(properties.stacksTo(1));

        this.basePowerCapacitySupplier = basePowerCapacitySupplier == null ? () -> 0 : basePowerCapacitySupplier;
        this.powerUpgradeSlots = Math.max(0, powerUpgradeSlots);
        this.maxPowerUpgrades = Mth.clamp(maxPowerUpgrades, 0, this.powerUpgradeSlots);
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
        if (player.isCreative()) {
            return true;
        }

        if (amount <= 0.0D) {
            return true;
        }

        int required = (int) Math.ceil(amount);

        if (getInternalPowerStored(stack) < required) {
            return false;
        }

        int extracted = extractInternalPower(stack, required, false);
        return extracted >= required;
    }

    protected int getInternalPowerCapacity(ItemStack stack) {
        int base = Math.max(0, this.basePowerCapacitySupplier.getAsInt());
        int upgrades = getInstalledPowerUpgrades(stack);

        long capacity = base + (long) base * upgrades;
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }

    protected int getInternalPowerStored(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }

        CompoundTag tag = stack.getTag();

        if (tag == null || !tag.contains(CURRENT_POWER_NBT_KEY, Tag.TAG_ANY_NUMERIC)) {
            return 0;
        }

        return Mth.clamp(
                tag.getInt(CURRENT_POWER_NBT_KEY),
                0,
                getInternalPowerCapacity(stack)
        );
    }

    protected int extractInternalPower(ItemStack stack, double amount, boolean simulate) {
        if (stack == null || stack.isEmpty() || amount <= 0.0D) {
            return 0;
        }

        int requested = (int) Math.ceil(amount);
        int stored = getInternalPowerStored(stack);
        int extracted = Math.min(stored, requested);

        if (!simulate && extracted > 0) {
            stack.getOrCreateTag().putInt(CURRENT_POWER_NBT_KEY, stored - extracted);
        }

        return extracted;
    }

    protected int receiveInternalPower(ItemStack stack, double amount, boolean simulate) {
        if (stack == null || stack.isEmpty() || amount <= 0.0D) {
            return 0;
        }

        int requested = (int) Math.ceil(amount);
        int capacity = getInternalPowerCapacity(stack);
        int stored = getInternalPowerStored(stack);
        int received = Math.min(capacity - stored, requested);

        if (!simulate && received > 0) {
            stack.getOrCreateTag().putInt(CURRENT_POWER_NBT_KEY, stored + received);
        }

        return received;
    }

    protected void refundEnergy(ItemStack stack, double amount) {
        receiveInternalPower(stack, amount, false);
    }

    protected double getCurrentPower(ItemStack stack) {
        return getInternalPowerStored(stack);
    }

    protected double getMaxPower(ItemStack stack) {
        return getInternalPowerCapacity(stack);
    }

    protected double getChargeRate(ItemStack stack) {
        return getInternalPowerCapacity(stack);
    }

    protected int getInstalledPowerUpgrades(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }

        CompoundTag tag = stack.getTag();

        if (tag == null || !tag.contains(POWER_UPGRADES_NBT_KEY, Tag.TAG_COMPOUND)) {
            return 0;
        }

        CompoundTag inventoryTag = tag.getCompound(POWER_UPGRADES_NBT_KEY);

        if (!inventoryTag.contains("Items", Tag.TAG_LIST)) {
            return 0;
        }

        ListTag items = inventoryTag.getList("Items", Tag.TAG_COMPOUND);
        int upgrades = 0;

        for (int i = 0; i < items.size(); i++) {
            CompoundTag row = items.getCompound(i);
            int slot = row.getInt("Slot");

            if (slot < 0 || slot >= this.maxPowerUpgrades) {
                continue;
            }

            if (!row.contains("Stack", Tag.TAG_COMPOUND)) {
                continue;
            }

            ItemStack stored = ItemStack.of(row.getCompound("Stack"));

            if (isValidPowerUpgradeItem(stored)) {
                upgrades++;
            }
        }

        return Mth.clamp(upgrades, 0, this.maxPowerUpgrades);
    }

    public static boolean isValidPowerUpgradeItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        ResourceLocation stackId = ForgeRegistries.ITEMS.getKey(stack.getItem());

        if (stackId == null) {
            return false;
        }

        for (ResourceLocation configuredId : getConfiguredPowerUpgradeItemIds()) {
            if (stackId.equals(configuredId)) {
                return true;
            }
        }

        return false;
    }

    public static List<ResourceLocation> getConfiguredPowerUpgradeItemIds() {
        LinkedHashSet<ResourceLocation> ids = new LinkedHashSet<>();

        for (String rawId : SpatialConfig.COMMON.ENERGY_UPGRADE_ITEMS.get()) {
            ResourceLocation id = ResourceLocation.tryParse(rawId);

            if (id != null) {
                ids.add(id);
            }
        }

        return List.copyOf(ids);
    }

    public static List<ItemStack> getConfiguredPowerUpgradeItemStacks() {
        ArrayList<ItemStack> stacks = new ArrayList<>();

        for (ResourceLocation id : getConfiguredPowerUpgradeItemIds()) {
            Item item = ForgeRegistries.ITEMS.getValue(id);

            if (item == null || item == Items.AIR) {
                continue;
            }

            stacks.add(new ItemStack(item));
        }

        return List.copyOf(stacks);
    }

    public static boolean isValidCraftingUpgradeItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (IsModLoaded.AE2) {
            return AE2Compat.isCraftingUpgradeItem(stack);
        }

        return false;
    }

    protected void showNotEnoughPower(Player player, ItemStack stack, double required) {
        int current = getInternalPowerStored(stack);
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

        clampStoredEnergy(stack);

        if (level.isClientSide() || !(entity instanceof Player player)) {
            return;
        }

        boolean isHeldNow = isHeldInHand(player, stack);
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

    private void clampStoredEnergy(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        int stored = getInternalPowerStored(stack);
        stack.getOrCreateTag().putInt(CURRENT_POWER_NBT_KEY, stored);
    }

    protected boolean isHeldInHand(Player player, ItemStack stack) {
        return player.getMainHandItem() == stack || player.getOffhandItem() == stack;
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

    private static void rememberSelectionDimension(ItemStack stack, Level level) {
        stack.getOrCreateTag().putString(
                SELECTION_DIMENSION_NBT_KEY,
                level.dimension().location().toString()
        );
    }

    private static void clearSelectionDimension(ItemStack stack) {
        CompoundTag tag = stack.getTag();

        if (tag != null) {
            tag.remove(SELECTION_DIMENSION_NBT_KEY);
        }
    }

    protected static void clearSelectionState(ItemStack stack) {
        StructureToolStackState.clearSelection(stack);
        StructureToolStackState.resetPreviewSideMap(stack);
        clearSelectionDimension(stack);
    }

    protected static boolean shouldSkipStructureToolBlock(
            Level level,
            BlockPos pos,
            BlockState state
    ) {
        if (state.isAir()) {
            return false;
        }

        if (state.is(Blocks.BEDROCK)
                || state.is(Blocks.NETHER_PORTAL)
                || state.is(Blocks.END_PORTAL)
                || state.is(Blocks.END_GATEWAY)
                || state.is(Blocks.BARRIER)
                || state.is(Blocks.COMMAND_BLOCK)
                || state.is(Blocks.CHAIN_COMMAND_BLOCK)
                || state.is(Blocks.REPEATING_COMMAND_BLOCK)
                || state.is(Blocks.STRUCTURE_BLOCK)
                || state.is(Blocks.STRUCTURE_VOID)
                || state.is(Blocks.JIGSAW)) {
            return true;
        }

        try {
            return state.getDestroySpeed(level, pos) < 0.0F;
        } catch (Throwable ignored) {
            return false;
        }
    }

    protected static CompoundTag filterUncapturableBlocksFromTemplate(
            Level level,
            BlockPos worldOrigin,
            CompoundTag templateTag
    ) {
        CompoundTag filtered = templateTag.copy();
        List<TemplateUtil.BlockInfo> parsedBlocks = TemplateUtil.parseRawBlocksFromTag(filtered);

        if (parsedBlocks.isEmpty()) {
            return filtered;
        }

        Set<BlockPos> skippedLocalPositions = new HashSet<>();

        for (TemplateUtil.BlockInfo info : parsedBlocks) {
            BlockPos localPos = info.pos();
            BlockPos worldPos = worldOrigin.offset(localPos);

            if (shouldSkipStructureToolBlock(level, worldPos, info.state())) {
                skippedLocalPositions.add(localPos);
            }
        }

        if (skippedLocalPositions.isEmpty()) {
            return filtered;
        }

        removeTemplateBlockEntriesAt(filtered, skippedLocalPositions);
        removeCloneMetadataEntriesAt(filtered, skippedLocalPositions);

        return filtered;
    }

    private static void removeTemplateBlockEntriesAt(
            CompoundTag templateTag,
            Set<BlockPos> skippedLocalPositions
    ) {
        if (!templateTag.contains("blocks", Tag.TAG_LIST)) {
            return;
        }

        ListTag oldBlocks = templateTag.getList("blocks", Tag.TAG_COMPOUND);
        ListTag newBlocks = new ListTag();

        for (int i = 0; i < oldBlocks.size(); i++) {
            CompoundTag blockEntry = oldBlocks.getCompound(i);
            BlockPos localPos = readTemplateBlockPos(blockEntry);

            if (localPos == null || !skippedLocalPositions.contains(localPos)) {
                newBlocks.add(blockEntry.copy());
            }
        }

        templateTag.put("blocks", newBlocks);
    }

    private static void removeCloneMetadataEntriesAt(
            CompoundTag templateTag,
            Set<BlockPos> skippedLocalPositions
    ) {
        if (!templateTag.contains(StructureToolKeys.CLONE_METADATA_KEY, Tag.TAG_COMPOUND)) {
            return;
        }

        CompoundTag cloneMetadata = templateTag.getCompound(StructureToolKeys.CLONE_METADATA_KEY).copy();

        if (!cloneMetadata.contains(StructureToolKeys.CLONE_METADATA_BLOCKS_KEY, Tag.TAG_LIST)) {
            templateTag.put(StructureToolKeys.CLONE_METADATA_KEY, cloneMetadata);
            return;
        }

        ListTag oldBlocks = cloneMetadata.getList(StructureToolKeys.CLONE_METADATA_BLOCKS_KEY, Tag.TAG_COMPOUND);
        ListTag newBlocks = new ListTag();

        for (int i = 0; i < oldBlocks.size(); i++) {
            CompoundTag blockEntry = oldBlocks.getCompound(i);
            BlockPos localPos = readCloneMetadataBlockPos(blockEntry);

            if (localPos == null || !skippedLocalPositions.contains(localPos)) {
                newBlocks.add(blockEntry.copy());
            }
        }

        cloneMetadata.put(StructureToolKeys.CLONE_METADATA_BLOCKS_KEY, newBlocks);
        templateTag.put(StructureToolKeys.CLONE_METADATA_KEY, cloneMetadata);
    }

    private static @Nullable BlockPos readTemplateBlockPos(CompoundTag blockEntry) {
        if (!blockEntry.contains("pos", Tag.TAG_LIST)) {
            return null;
        }

        ListTag posTag = blockEntry.getList("pos", Tag.TAG_INT);

        if (posTag.size() < 3) {
            return null;
        }

        return new BlockPos(
                posTag.getInt(0),
                posTag.getInt(1),
                posTag.getInt(2)
        );
    }

    private static @Nullable BlockPos readCloneMetadataBlockPos(CompoundTag blockEntry) {
        if (!blockEntry.contains(StructureToolKeys.CLONE_KEY_POS, Tag.TAG_COMPOUND)) {
            return null;
        }

        CompoundTag posTag = blockEntry.getCompound(StructureToolKeys.CLONE_KEY_POS);

        return new BlockPos(
                posTag.getInt("x"),
                posTag.getInt("y"),
                posTag.getInt("z")
        );
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

            if (player.isShiftKeyDown()) {
                openMenu(player, hand);
                return InteractionResultHolder.success(stack);
            }

            if (hasStructure) {
                onUseWithStoredStructure((ServerLevel) level, player, stack);
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

            BlockPos selectionA = StructureToolStackState.getSelectionA(stack);
            BlockPos selectionB = StructureToolStackState.getSelectionB(stack);

            if (selectionA == null) {
                StructureToolStackState.setSelectionA(stack, clickedPos.immutable());

                if (!level.isClientSide()) {
                    rememberSelectionDimension(stack, level);
                    showHud(player, Component.translatable(LangDefs.CORNER_A_SELECTED.getTranslationKey()));
                }
            } else if (selectionB == null) {
                StructureToolStackState.setSelectionB(stack, clickedPos.immutable());
                StructureToolStackState.setSourceFacing(stack, player.getDirection());

                if (!level.isClientSide()) {
                    showHud(player, Component.translatable(LangDefs.CORNER_B_SELECTED.getTranslationKey()));
                }
            } else {
                clearSelectionState(stack);

                if (!level.isClientSide()) {
                    showHud(player, Component.translatable(LangDefs.SELECTION_RESTARTED.getTranslationKey()));
                }
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

        if (!level.isClientSide() && isWaitingForSecondCorner(stack)) {
            selectSecondCorner((ServerLevel) level, player, stack);
        }

        return InteractionResult.sidedSuccess(level.isClientSide());
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

        BlockHitResult hit = StructureToolUtil.rayTrace(level, player, 50.0D);

        if (hit.getType() != HitResult.Type.BLOCK) {
            showHud(player, Component.translatable(LangDefs.NO_BLOCK_IN_RANGE.getTranslationKey()));
            return;
        }

        BlockPos pos = hit.getBlockPos().immutable();
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
        BlockPos size = max.subtract(min).offset(1, 1, 1);

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

        CapturedStructureResult result = new CapturedStructureResult(
                id,
                savedTag,
                min,
                max,
                origin,
                usedPower
        );

        afterStructureCaptured(level, player, stack, result);

        if (removeBlocks) {
            removeCapturedBlocksWithoutDrops(
                    level,
                    min,
                    savedTag,
                    filterUncapturable
            );
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

            level.setBlock(
                    worldPos,
                    air,
                    CUT_CLEAR_FLAGS,
                    0
            );
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

                    if (!requirementsHandled) {
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
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(pos),
                Direction.UP,
                pos,
                false
        );

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

        float ratio = Mth.clamp(
                getInternalPowerStored(stack) / (float) capacity,
                0.0F,
                1.0F
        );

        return Mth.hsvToRgb(ratio / 3.0F, 1.0F, 1.0F);
    }

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

    public int getCraftingUpgradeSlotIndex() {
        return this.powerUpgradeSlots > this.maxPowerUpgrades
                ? this.maxPowerUpgrades
                : -1;
    }

    public boolean hasCraftingUpgradeSlot() {
        return getCraftingUpgradeSlotIndex() >= 0;
    }

    public boolean isCraftingUpgradeSlot(int slot) {
        return slot == getCraftingUpgradeSlotIndex();
    }

    public boolean hasInstalledCraftingUpgrade(ItemStack stack) {
        int craftingSlot = getCraftingUpgradeSlotIndex();

        if (craftingSlot < 0 || stack == null || stack.isEmpty()) {
            return false;
        }

        CompoundTag tag = stack.getTag();

        if (tag == null || !tag.contains(POWER_UPGRADES_NBT_KEY, Tag.TAG_COMPOUND)) {
            return false;
        }

        CompoundTag inventoryTag = tag.getCompound(POWER_UPGRADES_NBT_KEY);

        if (!inventoryTag.contains("Items", Tag.TAG_LIST)) {
            return false;
        }

        ListTag items = inventoryTag.getList("Items", Tag.TAG_COMPOUND);

        for (int i = 0; i < items.size(); i++) {
            CompoundTag row = items.getCompound(i);

            if (row.getInt("Slot") != craftingSlot) {
                continue;
            }

            if (!row.contains("Stack", Tag.TAG_COMPOUND)) {
                continue;
            }

            return isValidCraftingUpgradeItem(ItemStack.of(row.getCompound("Stack")));
        }

        return false;
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
            clampStoredEnergy(this.containerStack);
        }

        private void loadFromStack() {
            this.loading = true;

            try {
                for (int slot = 0; slot < getSlots(); slot++) {
                    this.stacks.set(slot, ItemStack.EMPTY);
                }

                CompoundTag tag = this.containerStack.getTag();

                if (tag == null || !tag.contains(POWER_UPGRADES_NBT_KEY, Tag.TAG_COMPOUND)) {
                    return;
                }

                CompoundTag inventoryTag = tag.getCompound(POWER_UPGRADES_NBT_KEY);

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
            this.containerStack.getOrCreateTag().put(POWER_UPGRADES_NBT_KEY, inventoryTag);
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