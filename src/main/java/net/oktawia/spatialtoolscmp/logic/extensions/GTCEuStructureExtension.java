package net.oktawia.spatialtoolscmp.logic.extensions;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.machine.feature.IDataStickInteractable;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.pipenet.PipeCoverContainer;
import com.gregtechceu.gtceu.common.machine.electric.TransformerMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.oktawia.spatialtoolscmp.IsModLoaded;
import net.oktawia.spatialtoolscmp.compat.GTCEuAE2PostPasteOps;
import net.oktawia.spatialtoolscmp.SpatialToolsCMP;
import net.oktawia.spatialtoolscmp.items.AbstractStructureCaptureToolItem;
import net.oktawia.spatialtoolscmp.logic.ClonerPasteContext;
import net.oktawia.spatialtoolscmp.logic.PlacementPlan;
import net.oktawia.spatialtoolscmp.logic.StructureCloneExtension;
import net.oktawia.spatialtoolscmp.logic.StructurePasteExtension;
import net.oktawia.spatialtoolscmp.logic.StructureRemoveExtension;
import net.oktawia.spatialtoolscmp.util.NbtUtil;
import net.oktawia.spatialtoolscmp.util.StructureToolKeys;
import net.oktawia.spatialtoolscmp.util.TemplateUtil;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class GTCEuStructureExtension implements StructureCloneExtension, StructurePasteExtension, StructureRemoveExtension {

    private static final String NBT_ID = "id";
    private static final String NBT_COVER = "cover";
    private static final String NBT_TRANSFORM_UP = "isTransformUp";
    private static final String NBT_RENDER_STATE = "renderState";
    private static final String NBT_RENDER_STATE_NAME = "Name";
    private static final String NBT_RENDER_PROPERTIES = "Properties";
    private static final String NBT_RENDER_TRANSFORM_UP = "transform_up";
    private static final String NBT_DATA_STICK = "dataStick";
    private static final String NBT_DURATION_MULTIPLIER = "durationMultiplier";
    private static final String NBT_BUFFER_POS = "bufferPos";
    private static final String NBT_MY_BUFFER_POS = "myBufferPos";
    private static final String NBT_PATTERN_BUFFER_OFFSET = "patternBufferOffset";
    private static final String NBT_PATTERN_BUFFER_ID = "patternBufferId";
    private static final String NBT_IS_RANDOM_TICK_MODE = "isRandomTickMode";
    private static final String NBT_RENDER_RANDOM_TICK_MODE = "random_tick_mode";
    private static final String NBT_FE_TO_EU = "feToEu";
    private static final String NBT_RENDER_FE_TO_EU = "fe_to_eu";
    private static final String NBT_ENERGY_CONTAINER = "energyContainer";

    private static final long NEXT_TICK_DELAY = 1L;
    private static final long MULTIBLOCK_REFORM_DELAY = NEXT_TICK_DELAY + 1L;

    private static final Set<String> ENDER_LINK_COVER_IDS = Set.of(
            "gtceu:ender_item_link",
            "gtceu:ender_fluid_link",
            "gtceu:ender_redstone_link"
    );

    private static final List<PendingInit> PENDING = new ArrayList<>();
    private static boolean registered = false;

    @Override
    public boolean handlesRequirements(BlockState state, @Nullable CompoundTag rawBeTag) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());

        if (id != null && id.toString().startsWith(StructureToolKeys.GTCEU_ID_PREFIX)) {
            return true;
        }

        return isGregBlockEntityTag(rawBeTag);
    }

    @Override
    public boolean collectMetadata(
            ServerLevel level,
            BlockPos pos,
            BlockEntity be,
            @Nullable CompoundTag rawBeTag,
            AbstractStructureCaptureToolItem.RequirementSink requirements,
            CompoundTag blockEntry
    ) {
        boolean gregLike = handlesRequirements(level.getBlockState(pos), rawBeTag)
                || be instanceof MetaMachineBlockEntity
                || be instanceof PipeBlockEntity<?, ?>;

        if (gregLike) {
            addBaseBlockRequirement(level, pos, requirements);
        }

        CompoundTag gregData = collectGregMetadata(level, pos, rawBeTag, be, requirements);

        if (gregData.isEmpty()) {
            return false;
        }

        blockEntry.put(StructureToolKeys.CLONE_KEY_GREG, gregData);
        return true;
    }

    @Override
    public Optional<PlacementPlan> buildPlacementPlan(
            ServerLevel level,
            Player player,
            BlockState state,
            @Nullable CompoundTag rawBeTag,
            @Nullable CompoundTag blockMetadata,
            ClonerPasteContext ctx
    ) {
        if (!isGregBlockEntityTag(rawBeTag)) {
            return Optional.empty();
        }

        CompoundTag gregMeta = getGregMetadata(blockMetadata);

        PlacementPlan plan = isGregPipeTag(rawBeTag)
                ? buildGregPipePlacementPlan(player, state, rawBeTag, gregMeta, ctx)
                : buildGenericGregPlacementPlan(player, state, rawBeTag, gregMeta, ctx);

        return Optional.of(plan);
    }

    @Override
    public void onBlockPlaced(
            ServerLevel level,
            BlockPos pos,
            @Nullable BlockEntity be,
            @Nullable CompoundTag blockMetadata
    ) {
        CompoundTag gregMeta = getGregMetadata(blockMetadata);

        if (gregMeta.isEmpty()) {
            return;
        }

        CompoundTag currentTag = saveCurrentTag(be);

        CompoundTag postPlacementPipeTag = createPostPlacementPipeTag(be, currentTag, gregMeta);

        if (!postPlacementPipeTag.isEmpty()) {
            scheduleSinglePipePostPlacementInit(
                    level,
                    pos,
                    postPlacementPipeTag,
                    createCoverSnapshotForGuard(currentTag)
            );
            return;
        }

        CompoundTag machineData = gregMeta.getCompound(StructureToolKeys.CLONE_KEY_GREG_MACHINE);

        if (machineData.contains(NBT_BUFFER_POS, Tag.TAG_COMPOUND)
                && machineData.contains(NBT_PATTERN_BUFFER_ID, Tag.TAG_STRING)) {
            scheduleSinglePatternBufferLinkRefresh(
                    level,
                    pos,
                    machineData
            );
        }

        if (hasStoredGregTransformerState(machineData)) {
            scheduleSingleTransformerStateRefresh(
                    level,
                    pos,
                    machineData
            );
        }

        if (hasStoredWorldAcceleratorMode(machineData)) {
            scheduleSingleWorldAcceleratorModeRefresh(
                    level,
                    pos,
                    machineData
            );
        }

        if (hasStoredEnergyConverterDirection(machineData)) {
            scheduleSingleEnergyConverterDirectionRefresh(
                    level,
                    pos,
                    machineData
            );
        }

        if (machineData.contains(NBT_DATA_STICK, Tag.TAG_COMPOUND)) {
            scheduleSingleDataStickApply(
                    level,
                    pos,
                    machineData.getCompound(NBT_DATA_STICK)
            );
        }

        reapplyFluidTankLockFilters(be);
        reapplyPaintingColor(be);
        reinitMachineCovers(be);

        if (be instanceof MetaMachineBlockEntity
                && hasAnyCover(gregMeta.getCompound(StructureToolKeys.CLONE_KEY_GREG_COVER))) {
            scheduleSingleMachineCoverReinit(level, pos);
        }
    }

    private static void reinitMachineCovers(@Nullable BlockEntity be) {
        if (!(be instanceof MetaMachineBlockEntity mmbe)) {
            return;
        }
        mmbe.getMetaMachine().getCoverContainer().onLoad();
    }

    private static boolean reinitMachineCoversDeferred(@Nullable BlockEntity be) {
        if (!(be instanceof MetaMachineBlockEntity mmbe)) {
            return false;
        }

        var coverContainer = mmbe.getMetaMachine().getCoverContainer();

        boolean any = false;

        for (Direction side : Direction.values()) {
            CoverBehavior cover = coverContainer.getCoverAtSide(side);

            if (cover == null) {
                continue;
            }

            coverContainer.setCoverAtSide(cover, side);

            try {
                cover.onLoad();
            } catch (Throwable ignored) {
            }

            try {
                cover.getSyncStorage().markAllDirty();
            } catch (Throwable ignored) {
            }

            any = true;
        }

        return any;
    }

    private static void reapplyFluidTankLockFilters(@Nullable BlockEntity be) {
        if (!(be instanceof MetaMachineBlockEntity mmbe)) {
            return;
        }

        for (MachineTrait trait : mmbe.getMetaMachine().getTraits()) {
            if (!(trait instanceof NotifiableFluidTank tank)) {
                continue;
            }

            if (tank.isLocked()) {
                tank.setFilter(stack -> stack.isFluidEqual(tank.getLockedFluid().getFluid()));
            }
        }
    }

    private static void reapplyPaintingColor(@Nullable BlockEntity be) {
        if (!(be instanceof MetaMachineBlockEntity mmbe)) {
            return;
        }

        var machine = mmbe.getMetaMachine();

        if (!machine.isPainted()) {
            return;
        }

        int color = machine.getPaintingColor();

        machine.setPaintingColor(machine.getDefaultPaintingColor());
        machine.setPaintingColor(color);
    }

    private static void reformMultiblock(ServerLevel level, @Nullable BlockEntity be) {
        if (!(be instanceof MetaMachineBlockEntity mmbe)
                || !(mmbe.getMetaMachine() instanceof IMultiController controller)) {
            return;
        }

        try {
            MultiblockWorldSavedData mwsd = MultiblockWorldSavedData.getOrCreate(level);
            MultiblockState state = controller.getMultiblockState();

            // isFormed is @Persisted, so a pasted controller wakes up already "formed" with an empty part list.
            // GTCEu gates asyncCheckPattern on hasError() || !isFormed, so the stale flag stops it from ever
            // running onStructureFormed again. Clearing it hands the whole lifecycle back to GTCEu.
            controller.onStructureInvalid();
            mwsd.removeMapping(state);
            state.setError(MultiblockState.UNINIT_ERROR);
            mwsd.addAsyncLogic(controller);

            SpatialToolsCMP.getLOGGER().info(
                    "[gt] queued multiblock recheck at {} front={} upwards={} flipped={}",
                    controller.self().getPos(),
                    controller.self().getFrontFacing(),
                    controller.self().getUpwardsFacing(),
                    controller.self().isFlipped()
            );
        } catch (Throwable throwable) {
            SpatialToolsCMP.getLOGGER().warn("[gt] multiblock recheck failed at {}", mmbe.getBlockPos(), throwable);
        }
    }

    private static PlacementPlan buildGregPipePlacementPlan(
            Player player,
            BlockState pipeState,
            CompoundTag rawBeTag,
            CompoundTag gregMeta,
            ClonerPasteContext ctx
    ) {
        CompoundTag pipeData = gregMeta.getCompound(StructureToolKeys.CLONE_KEY_GREG_PIPE);
        CompoundTag coverData = gregMeta.getCompound(StructureToolKeys.CLONE_KEY_GREG_COVER);

        ItemStack pipeItem = normalizeSingle(ctx.getRequiredBlockItem(pipeState));
        String frameMaterial = pipeData.getString("frameMaterial");

        ItemStack frameItem = normalizeSingle(getGregFrameItem(frameMaterial));
        BlockState frameState = getGregFrameState(frameMaterial);

        if (player.isCreative()) {
            List<ItemStack> costs = new ArrayList<>();

            if (!pipeItem.isEmpty()) {
                costs.add(pipeItem);
            }

            if (!frameItem.isEmpty()) {
                costs.add(frameItem);
            }

            CompoundTag filteredCover = filterGregCoverForPlacement(
                    coverData,
                    null,
                    true,
                    costs,
                    null
            );

            CompoundTag beTag = createWhitelistedGregPipeTag(rawBeTag, pipeData, filteredCover);
            return new PlacementPlan(true, pipeState, beTag, costs);
        }

        Map<Item, Integer> reserved = new LinkedHashMap<>();
        List<ItemStack> costs = new ArrayList<>();

        boolean canPlacePipe = !pipeItem.isEmpty()
                && ctx.canReserveForPaste(reserved, pipeItem, 1);

        if (canPlacePipe) {
            costs.add(pipeItem);

            CompoundTag effectivePipeData = pipeData.copy();

            if (!frameItem.isEmpty()) {
                if (ctx.canReserveForPaste(reserved, frameItem, 1)) {
                    costs.add(frameItem);
                } else {
                    effectivePipeData.remove("frameMaterial");
                }
            } else {
                effectivePipeData.remove("frameMaterial");
            }

            CompoundTag filteredCover = filterGregCoverForPlacement(
                    coverData,
                    reserved,
                    false,
                    costs,
                    ctx
            );

            CompoundTag beTag = createWhitelistedGregPipeTag(rawBeTag, effectivePipeData, filteredCover);

            return new PlacementPlan(true, pipeState, beTag, costs);
        }

        if (!frameItem.isEmpty() && frameState != null && ctx.countAvailableForPaste(frameItem) > 0) {
            return new PlacementPlan(true, frameState, null, List.of(frameItem));
        }

        return PlacementPlan.none();
    }

    private static PlacementPlan buildGenericGregPlacementPlan(
            Player player,
            BlockState stateToPlace,
            CompoundTag rawBeTag,
            CompoundTag gregMeta,
            ClonerPasteContext ctx
    ) {
        CompoundTag machineData = gregMeta.getCompound(StructureToolKeys.CLONE_KEY_GREG_MACHINE);
        CompoundTag coverData = gregMeta.getCompound(StructureToolKeys.CLONE_KEY_GREG_COVER);

        ItemStack baseItem = normalizeSingle(ctx.getRequiredBlockItem(stateToPlace));

        if (baseItem.isEmpty() && !player.isCreative()) {
            return PlacementPlan.none();
        }

        if (player.isCreative()) {
            List<ItemStack> costs = new ArrayList<>();

            if (!baseItem.isEmpty()) {
                costs.add(baseItem);
            }

            CompoundTag filteredCover = filterGregCoverForPlacement(
                    coverData,
                    null,
                    true,
                    costs,
                    null
            );

            CompoundTag beTag = createWhitelistedGregMachineTag(rawBeTag, machineData, filteredCover);

            return new PlacementPlan(true, stateToPlace, beTag, costs);
        }

        Map<Item, Integer> reserved = new LinkedHashMap<>();

        if (!ctx.canReserveForPaste(reserved, baseItem, 1)) {
            return PlacementPlan.none();
        }

        List<ItemStack> costs = new ArrayList<>();
        costs.add(baseItem);

        CompoundTag filteredCover = filterGregCoverForPlacement(
                coverData,
                reserved,
                false,
                costs,
                ctx
        );

        CompoundTag beTag = createWhitelistedGregMachineTag(rawBeTag, machineData, filteredCover);

        return new PlacementPlan(true, stateToPlace, beTag, costs);
    }

    private static CompoundTag filterGregCoverForPlacement(
            CompoundTag coverTag,
            @Nullable Map<Item, Integer> reserved,
            boolean creative,
            @Nullable List<ItemStack> costs,
            @Nullable ClonerPasteContext ctx
    ) {
        CompoundTag filteredCover = new CompoundTag();

        if (coverTag == null || coverTag.isEmpty()) {
            return filteredCover;
        }

        for (String sideKey : coverTag.getAllKeys()) {
            Tag sideTag = coverTag.get(sideKey);

            if (sideTag == null) {
                continue;
            }

            List<ItemStack> attachItems = new ArrayList<>();

            collectGregAttachItems(sideTag, item -> {
                ItemStack normalized = normalizeCostStack(item);

                if (!normalized.isEmpty()) {
                    attachItems.add(normalized);
                }
            });

            boolean keepSide = true;

            if (!creative && !attachItems.isEmpty()) {
                if (reserved == null || ctx == null) {
                    keepSide = false;
                } else {
                    for (ItemStack attachItem : attachItems) {
                        int amount = Math.max(1, attachItem.getCount());

                        ItemStack wanted = attachItem.copy();
                        wanted.setCount(1);
                        wanted.setTag(null);

                        if (!ctx.canReserveForPaste(reserved, wanted, amount)) {
                            keepSide = false;
                            break;
                        }
                    }
                }
            }

            if (keepSide) {
                filteredCover.put(sideKey, sideTag.copy());

                if (costs != null) {
                    costs.addAll(attachItems);
                }
            }
        }

        return filteredCover;
    }

    private static ItemStack normalizeCostStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack cost = stack.copy();

        int amount = Math.max(1, cost.getCount());

        cost.setCount(amount);
        cost.setTag(null);

        return cost;
    }

    private static CompoundTag collectGregMetadata(
            ServerLevel level,
            BlockPos pos,
            @Nullable CompoundTag rawBeTag,
            BlockEntity be,
            AbstractStructureCaptureToolItem.RequirementSink requirements
    ) {
        CompoundTag out = new CompoundTag();

        boolean pipeLike = be instanceof PipeBlockEntity<?, ?> || isGregPipeTag(rawBeTag);
        boolean machineLike = be instanceof MetaMachineBlockEntity || isGregMachineTag(rawBeTag);

        if (!pipeLike && !machineLike && !isGregBlockEntityTag(rawBeTag)) {
            return out;
        }

        if (rawBeTag != null) {
            NbtUtil.copyStringIfPresent(rawBeTag, out, NBT_ID);
        }

        if (rawBeTag != null && rawBeTag.contains(NBT_COVER, Tag.TAG_COMPOUND)) {
            CompoundTag coverTag = rawBeTag.getCompound(NBT_COVER).copy();

            out.put(StructureToolKeys.CLONE_KEY_GREG_COVER, coverTag);
            collectGregCoverRequirements(coverTag, requirements);
        }

        if (pipeLike && rawBeTag != null) {
            CompoundTag pipeTag = new CompoundTag();

            NbtUtil.copyIntIfPresent(rawBeTag, pipeTag, "connections");
            NbtUtil.copyIntIfPresent(rawBeTag, pipeTag, "blockedConnections");
            NbtUtil.copyIntIfPresent(rawBeTag, pipeTag, "paintingColor");

            if (rawBeTag.contains("frameMaterial", Tag.TAG_STRING)) {
                String frameMaterial = rawBeTag.getString("frameMaterial");

                pipeTag.putString("frameMaterial", frameMaterial);
                collectGregPipeFrameRequirement(frameMaterial, requirements);
            }

            if (!pipeTag.isEmpty()) {
                out.put(StructureToolKeys.CLONE_KEY_GREG_PIPE, pipeTag);
            }
        }

        if (machineLike && rawBeTag != null) {
            CompoundTag machineTag = new CompoundTag();

            NbtUtil.copyTagIfPresent(rawBeTag, machineTag, "ownerUUID");
            NbtUtil.copyStringIfPresent(rawBeTag, machineTag, "workingMode");
            NbtUtil.copyStringIfPresent(rawBeTag, machineTag, "voidingMode");
            NbtUtil.copyByteIfPresent(rawBeTag, machineTag, "batchEnabled");
            NbtUtil.copyByteIfPresent(rawBeTag, machineTag, "isWorkingEnabled");
            NbtUtil.copyByteIfPresent(rawBeTag, machineTag, "workingEnabled");
            NbtUtil.copyByteIfPresent(rawBeTag, machineTag, "isMuffled");
            NbtUtil.copyByteIfPresent(rawBeTag, machineTag, "isDistinct");
            NbtUtil.copyIntIfPresent(rawBeTag, machineTag, "paintingColor");
            NbtUtil.copyIntIfPresent(rawBeTag, machineTag, "currentParallel");
            NbtUtil.copyIntIfPresent(rawBeTag, machineTag, "minStackSize");
            NbtUtil.copyIntIfPresent(rawBeTag, machineTag, "ticksPerCycle");

            if (rawBeTag.contains(NBT_DURATION_MULTIPLIER, Tag.TAG_ANY_NUMERIC)) {
                machineTag.putFloat(NBT_DURATION_MULTIPLIER, rawBeTag.getFloat(NBT_DURATION_MULTIPLIER));
            }

            collectPatternBufferLinkMetadata(level, pos, rawBeTag, machineTag);

            NbtUtil.copyTagIfPresent(rawBeTag, machineTag, "circuitInventory");
            NbtUtil.copyIntIfPresent(rawBeTag, machineTag, "activeRecipeType");

            if (isGregTransformerTag(rawBeTag)) {
                copyGregTransformerState(rawBeTag, machineTag);
            }

            if (isWorldAcceleratorTag(rawBeTag)) {
                copyWorldAcceleratorState(rawBeTag, machineTag);
            }

            if (isEnergyConverterTag(rawBeTag)) {
                copyEnergyConverterState(rawBeTag, machineTag);
            }

            copyGregMachineSideConfig(rawBeTag, machineTag);

            CompoundTag dataStick = collectGregDataStick(be);

            if (!dataStick.isEmpty()) {
                machineTag.put(NBT_DATA_STICK, dataStick);
            }

            if (!machineTag.isEmpty()) {
                out.put(StructureToolKeys.CLONE_KEY_GREG_MACHINE, machineTag);
            }
        }

        return out;
    }

    @Nullable
    private static BlockPos readMachinePosFromTag(@Nullable CompoundTag beTag) {
        if (beTag == null
                || !beTag.contains("x", Tag.TAG_ANY_NUMERIC)
                || !beTag.contains("y", Tag.TAG_ANY_NUMERIC)
                || !beTag.contains("z", Tag.TAG_ANY_NUMERIC)) {
            return null;
        }

        return new BlockPos(beTag.getInt("x"), beTag.getInt("y"), beTag.getInt("z"));
    }

    private static CompoundTag buildPatternBufferLinkDataFromTemplateTag(
            ServerLevel level,
            CompoundTag templateBeTag
    ) {
        CompoundTag linkData = new CompoundTag();

        if (templateBeTag == null || !isPatternBufferProxyTag(templateBeTag)) {
            return linkData;
        }

        BlockPos originalProxyPos = readMachinePosFromTag(templateBeTag);

        if (originalProxyPos == null) {
            return linkData;
        }

        collectPatternBufferLinkMetadata(level, originalProxyPos, templateBeTag, linkData);
        return linkData;
    }

    private static void collectPatternBufferLinkMetadata(
            ServerLevel level,
            BlockPos proxyPos,
            CompoundTag rawBeTag,
            CompoundTag machineTag
    ) {
        String bufPosKey = rawBeTag.contains(NBT_BUFFER_POS, Tag.TAG_COMPOUND) ? NBT_BUFFER_POS
                : rawBeTag.contains(NBT_MY_BUFFER_POS, Tag.TAG_COMPOUND) ? NBT_MY_BUFFER_POS
                : null;

        if (bufPosKey == null) {
            return;
        }

        if (!isPatternBufferProxyTag(rawBeTag)) {
            return;
        }

        String proxyId = getMachineId(rawBeTag);
        String expectedBufferId = getExpectedPatternBufferIdForProxyId(proxyId);

        if (expectedBufferId.isBlank()) {
            return;
        }

        CompoundTag originalBufferPosTag = rawBeTag.getCompound(bufPosKey).copy();
        BlockPos originalBufferPos = readBlockPosTag(originalBufferPosTag);

        if (originalBufferPos == null) {
            return;
        }

        machineTag.put(NBT_BUFFER_POS, originalBufferPosTag);
        machineTag.putString(NBT_PATTERN_BUFFER_ID, expectedBufferId);
        writePatternBufferOffset(proxyPos, originalBufferPos, machineTag);
    }

    private static void writePatternBufferOffset(
            BlockPos proxyPos,
            BlockPos bufferPos,
            CompoundTag machineTag
    ) {
        CompoundTag offsetTag = new CompoundTag();

        offsetTag.putInt("x", bufferPos.getX() - proxyPos.getX());
        offsetTag.putInt("y", bufferPos.getY() - proxyPos.getY());
        offsetTag.putInt("z", bufferPos.getZ() - proxyPos.getZ());

        machineTag.put(NBT_PATTERN_BUFFER_OFFSET, offsetTag);
    }

    private static boolean applyPatternBufferLink(
            ServerLevel level,
            BlockPos pastedProxyPos,
            @Nullable BlockEntity be,
            CompoundTag machineData
    ) {
        if (be == null) {
            return false;
        }

        if (!machineData.contains(NBT_BUFFER_POS, Tag.TAG_COMPOUND)) {
            return false;
        }

        BlockPos originalBufferPos = readBlockPosTag(machineData.getCompound(NBT_BUFFER_POS));

        if (originalBufferPos == null) {
            return false;
        }

        String expectedBufferId = machineData.getString(NBT_PATTERN_BUFFER_ID);

        if (expectedBufferId.isBlank()) {
            CompoundTag currentTagForId = saveCurrentTag(be);

            if (currentTagForId != null) {
                expectedBufferId = getExpectedPatternBufferIdForProxyId(getMachineId(currentTagForId));
            }
        }

        if (expectedBufferId.isBlank()) {
            return false;
        }

        BlockPos finalBufferPos = originalBufferPos;

        if (machineData.contains(NBT_PATTERN_BUFFER_OFFSET, Tag.TAG_COMPOUND)) {
            BlockPos offset = readBlockPosTag(machineData.getCompound(NBT_PATTERN_BUFFER_OFFSET));

            if (offset != null) {
                BlockPos candidateBufferPos = pastedProxyPos.offset(
                        offset.getX(),
                        offset.getY(),
                        offset.getZ()
                );

                if (isExpectedPatternBufferAt(level, candidateBufferPos, expectedBufferId)) {
                    finalBufferPos = candidateBufferPos;
                }
            }
        }

        CompoundTag currentTag = saveCurrentTag(be);

        if (currentTag == null) {
            return false;
        }

        boolean isInsaneProxy = isInsaneMePatternBufferProxyId(getMachineId(currentTag));

        if (isInsaneProxy) {
            if (be instanceof MetaMachineBlockEntity mmbe
                    && mmbe.getMetaMachine() instanceof IDataStickInteractable interactable) {
                ItemStack stick = new ItemStack(Items.STICK);
                CompoundTag stickTag = new CompoundTag();
                stickTag.putIntArray("pos", new int[]{
                        finalBufferPos.getX(), finalBufferPos.getY(), finalBufferPos.getZ()
                });
                stick.setTag(stickTag);
                try {
                    InteractionResult result = interactable.onDataStickUse(
                            FakePlayerFactory.getMinecraft(level), stick);
                    if (result.consumesAction() || result == InteractionResult.SUCCESS) {
                        syncGenericGregBlockEntityNoLoad(level, pastedProxyPos, be);
                        return true;
                    }
                } catch (Throwable ignored) {
                }
            }
            currentTag.put(NBT_MY_BUFFER_POS, writeUpperBlockPosTag(finalBufferPos));
        } else {
            currentTag.put(NBT_BUFFER_POS, writeUpperBlockPosTag(finalBufferPos));
        }

        currentTag.remove(NBT_PATTERN_BUFFER_OFFSET);
        currentTag.remove(NBT_PATTERN_BUFFER_ID);

        currentTag.putInt("x", pastedProxyPos.getX());
        currentTag.putInt("y", pastedProxyPos.getY());
        currentTag.putInt("z", pastedProxyPos.getZ());

        try {
            be.load(currentTag);
            be.clearRemoved();
        } catch (Throwable ignored) {
            return false;
        }

        syncGenericGregBlockEntityNoLoad(level, pastedProxyPos, be);
        return true;
    }

    private static boolean isExpectedPatternBufferAt(
            ServerLevel level,
            BlockPos pos,
            String expectedBufferId
    ) {
        if (expectedBufferId == null || expectedBufferId.isBlank()) {
            return false;
        }

        String normalizedExpected = expectedBufferId.toLowerCase(Locale.ROOT);

        BlockState state = level.getBlockState(pos);
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());

        if (blockId != null && blockId.toString().toLowerCase(Locale.ROOT).equals(normalizedExpected)) {
            return true;
        }

        BlockEntity be = level.getBlockEntity(pos);

        if (be == null) {
            return false;
        }

        CompoundTag tag = saveCurrentTag(be);

        if (tag == null) {
            return false;
        }

        String actualId = getMachineId(tag).toLowerCase(Locale.ROOT);

        return actualId.equals(normalizedExpected);
    }

    private static boolean isPatternBufferTag(CompoundTag tag) {
        String id = getMachineId(tag);

        return isPatternBufferId(id);
    }

    private static boolean isPatternBufferProxyTag(CompoundTag tag) {
        String id = getMachineId(tag);

        return isPatternBufferProxyId(id);
    }

    private static boolean isPatternBufferId(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }

        String normalized = id.toLowerCase(Locale.ROOT);

        return normalized.contains("pattern_buffer")
                && !isPatternBufferProxyId(normalized);
    }

    private static boolean isPatternBufferProxyId(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }

        String normalized = id.toLowerCase(Locale.ROOT);

        return normalized.contains("pattern_buffer_proxy")
                || normalized.contains("pattern_buffer") && normalized.endsWith("_proxy");
    }

    private static boolean isInsaneMePatternBufferProxyId(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }

        String normalized = id.toLowerCase(Locale.ROOT);

        return normalized.contains("insane") && isPatternBufferProxyId(normalized);
    }

    private static String getExpectedPatternBufferIdForProxyId(String proxyId) {
        if (proxyId == null || proxyId.isBlank()) {
            return "";
        }

        String normalized = proxyId.toLowerCase(Locale.ROOT);

        if (!isPatternBufferProxyId(normalized)) {
            return "";
        }

        if (normalized.endsWith("_proxy")) {
            return normalized.substring(0, normalized.length() - "_proxy".length());
        }

        if (normalized.contains("pattern_buffer_proxy")) {
            return normalized.replace("pattern_buffer_proxy", "pattern_buffer");
        }

        return "";
    }

    private static String getMachineId(CompoundTag tag) {
        String id = tag.getString(NBT_ID);

        if (!id.isBlank()) {
            return id;
        }

        if (tag.contains(NBT_RENDER_STATE, Tag.TAG_COMPOUND)) {
            CompoundTag renderState = tag.getCompound(NBT_RENDER_STATE);
            String renderName = renderState.getString(NBT_RENDER_STATE_NAME);

            if (!renderName.isBlank()) {
                return renderName;
            }
        }

        return "";
    }

    @Nullable
    private static BlockPos readBlockPosTag(CompoundTag tag) {
        if (hasBlockPosTag(tag, "X", "Y", "Z")) {
            return new BlockPos(
                    tag.getInt("X"),
                    tag.getInt("Y"),
                    tag.getInt("Z")
            );
        }

        if (hasBlockPosTag(tag, "x", "y", "z")) {
            return new BlockPos(
                    tag.getInt("x"),
                    tag.getInt("y"),
                    tag.getInt("z")
            );
        }

        return null;
    }

    private static boolean hasBlockPosTag(
            CompoundTag tag,
            String xKey,
            String yKey,
            String zKey
    ) {
        return tag.contains(xKey, Tag.TAG_ANY_NUMERIC)
                && tag.contains(yKey, Tag.TAG_ANY_NUMERIC)
                && tag.contains(zKey, Tag.TAG_ANY_NUMERIC);
    }

    private static CompoundTag writeUpperBlockPosTag(BlockPos pos) {
        CompoundTag tag = new CompoundTag();

        tag.putInt("X", pos.getX());
        tag.putInt("Y", pos.getY());
        tag.putInt("Z", pos.getZ());

        return tag;
    }

    private static CompoundTag collectGregDataStick(BlockEntity be) {
        if (!(be instanceof MetaMachineBlockEntity mmbe)) {
            return new CompoundTag();
        }

        if (!(mmbe.getMetaMachine() instanceof IDataStickInteractable interactable)) {
            return new CompoundTag();
        }

        if (!(be.getLevel() instanceof ServerLevel serverLevel)) {
            return new CompoundTag();
        }

        ItemStack stick = new ItemStack(Items.STICK);
        Player fakePlayer = FakePlayerFactory.getMinecraft(serverLevel);

        try {
            InteractionResult result = interactable.onDataStickShiftUse(fakePlayer, stick);

            if (!result.consumesAction() && result != InteractionResult.SUCCESS) {
                return new CompoundTag();
            }

            CompoundTag tag = stick.getTag();
            return tag == null ? new CompoundTag() : tag.copy();
        } catch (Throwable ignored) {
            return new CompoundTag();
        }
    }

    private static void collectGregPipeFrameRequirement(
            String frameMaterial,
            AbstractStructureCaptureToolItem.RequirementSink requirements
    ) {
        if (frameMaterial == null || frameMaterial.isBlank()) {
            return;
        }

        String materialPath = frameMaterial;
        int namespaceSeparator = materialPath.indexOf(':');

        if (namespaceSeparator >= 0 && namespaceSeparator + 1 < materialPath.length()) {
            materialPath = materialPath.substring(namespaceSeparator + 1);
        }

        ResourceLocation frameId = new ResourceLocation("gtceu", materialPath + "_frame");
        Item frameItem = ForgeRegistries.ITEMS.getValue(frameId);

        if (frameItem != null && frameItem != Items.AIR) {
            requirements.add(new ItemStack(frameItem));
        }
    }

    private static void copyGregMachineSideConfig(CompoundTag from, CompoundTag to) {
        NbtUtil.copyTagIfPresent(from, to, "outputFacingItems");
        NbtUtil.copyTagIfPresent(from, to, "outputFacingFluids");

        NbtUtil.copyTagIfPresent(from, to, "inputFacingItems");
        NbtUtil.copyTagIfPresent(from, to, "inputFacingFluids");

        NbtUtil.copyByteIfPresent(from, to, "autoOutputItems");
        NbtUtil.copyByteIfPresent(from, to, "autoOutputFluids");

        NbtUtil.copyByteIfPresent(from, to, "allowInputFromOutputSideItems");
        NbtUtil.copyByteIfPresent(from, to, "allowInputFromOutputSideFluids");

        copyGregLockedFluidSettings(from, to);
    }

    private static void copyGregLockedFluidSettings(CompoundTag from, CompoundTag to) {
        copyGregLockedFluidSettingsRecursive(from, to);
    }

    private static void copyGregLockedFluidSettingsRecursive(CompoundTag from, CompoundTag to) {
        for (String key : from.getAllKeys()) {
            Tag value = from.get(key);

            if (value == null) {
                continue;
            }

            if (isGregLockedFluidKey(key)) {
                to.put(key, value.copy());
                continue;
            }

            if (value instanceof CompoundTag childFrom) {
                CompoundTag childTo = to.contains(key, Tag.TAG_COMPOUND)
                        ? to.getCompound(key).copy()
                        : new CompoundTag();

                copyGregLockedFluidSettingsRecursive(childFrom, childTo);

                if (!childTo.isEmpty()) {
                    to.put(key, childTo);
                }
            }
        }
    }

    private static boolean isGregLockedFluidKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);

        return normalized.equals("lockedfluid")
                || normalized.equals("lockedfluids")
                || normalized.equals("lockedfluidname")
                || normalized.equals("lockedfluidstack")
                || normalized.equals("filterfluid")
                || normalized.equals("fluidfilter");
    }

    private static void collectGregCoverRequirements(
            CompoundTag coverTag,
            AbstractStructureCaptureToolItem.RequirementSink requirements
    ) {
        for (String sideKey : coverTag.getAllKeys()) {
            collectGregAttachItems(coverTag.get(sideKey), requirements::add);
        }
    }

    private static CompoundTag createWhitelistedGregPipeTag(
            CompoundTag rawBeTag,
            CompoundTag pipeData,
            CompoundTag filteredCover
    ) {
        CompoundTag out = new CompoundTag();

        NbtUtil.copyStringIfPresent(rawBeTag, out, NBT_ID);
        NbtUtil.copyIntIfPresent(pipeData, out, "connections");
        NbtUtil.copyIntIfPresent(pipeData, out, "blockedConnections");
        NbtUtil.copyIntIfPresent(pipeData, out, "paintingColor");
        NbtUtil.copyStringIfPresent(pipeData, out, "frameMaterial");

        if (!filteredCover.isEmpty()) {
            out.put(NBT_COVER, filteredCover.copy());
        }

        return out;
    }

    private static CompoundTag createWhitelistedGregMachineTag(
            CompoundTag rawBeTag,
            CompoundTag machineData,
            CompoundTag filteredCover
    ) {
        CompoundTag out = new CompoundTag();

        NbtUtil.copyStringIfPresent(rawBeTag, out, NBT_ID);
        NbtUtil.copyTagIfPresent(machineData, out, "ownerUUID");
        NbtUtil.copyStringIfPresent(machineData, out, "workingMode");
        NbtUtil.copyStringIfPresent(machineData, out, "voidingMode");
        NbtUtil.copyByteIfPresent(machineData, out, "batchEnabled");
        NbtUtil.copyByteIfPresent(machineData, out, "isWorkingEnabled");
        NbtUtil.copyByteIfPresent(machineData, out, "workingEnabled");
        NbtUtil.copyByteIfPresent(machineData, out, "isMuffled");
        NbtUtil.copyByteIfPresent(machineData, out, "isDistinct");
        NbtUtil.copyIntIfPresent(machineData, out, "paintingColor");
        NbtUtil.copyIntIfPresent(machineData, out, "currentParallel");
        NbtUtil.copyIntIfPresent(machineData, out, "minStackSize");
        NbtUtil.copyIntIfPresent(machineData, out, "ticksPerCycle");

        if (machineData.contains(NBT_DURATION_MULTIPLIER, Tag.TAG_ANY_NUMERIC)) {
            out.putFloat(NBT_DURATION_MULTIPLIER, machineData.getFloat(NBT_DURATION_MULTIPLIER));
        }

        NbtUtil.copyTagIfPresent(machineData, out, NBT_BUFFER_POS);

        NbtUtil.copyTagIfPresent(machineData, out, "circuitInventory");
        NbtUtil.copyIntIfPresent(machineData, out, "activeRecipeType");

        copyGregTransformerState(machineData, out);
        copyWorldAcceleratorState(machineData, out);
        copyEnergyConverterState(machineData, out);
        copyGregMachineSideConfig(machineData, out);

        if (machineData.contains(NBT_DATA_STICK, Tag.TAG_COMPOUND)) {
            out.put(NBT_DATA_STICK, machineData.getCompound(NBT_DATA_STICK).copy());
        }

        if (!filteredCover.isEmpty()) {
            out.put(NBT_COVER, filteredCover.copy());
        }

        return out;
    }

    private static boolean isGregTransformerTag(@Nullable CompoundTag tag) {
        if (tag == null) {
            return false;
        }

        String id = tag.getString(NBT_ID).toLowerCase(Locale.ROOT);

        if (id.contains("transformer")) {
            return true;
        }

        if (tag.contains(NBT_TRANSFORM_UP, Tag.TAG_BYTE)) {
            return true;
        }

        if (!tag.contains(NBT_RENDER_STATE, Tag.TAG_COMPOUND)) {
            return false;
        }

        CompoundTag renderState = tag.getCompound(NBT_RENDER_STATE);

        String renderName = renderState.getString(NBT_RENDER_STATE_NAME).toLowerCase(Locale.ROOT);

        if (renderName.contains("transformer")) {
            return true;
        }

        if (!renderState.contains(NBT_RENDER_PROPERTIES, Tag.TAG_COMPOUND)) {
            return false;
        }

        CompoundTag properties = renderState.getCompound(NBT_RENDER_PROPERTIES);

        return properties.contains(NBT_RENDER_TRANSFORM_UP, Tag.TAG_STRING);
    }

    private static void copyGregTransformerState(CompoundTag from, CompoundTag to) {
        NbtUtil.copyByteIfPresent(from, to, NBT_TRANSFORM_UP);

        CompoundTag renderState = copyGregTransformerRenderState(from);

        if (!renderState.isEmpty()) {
            to.put(NBT_RENDER_STATE, renderState);
        }
    }

    private static CompoundTag copyGregTransformerRenderState(CompoundTag from) {
        CompoundTag out = new CompoundTag();

        if (!from.contains(NBT_RENDER_STATE, Tag.TAG_COMPOUND)) {
            return out;
        }

        CompoundTag renderState = from.getCompound(NBT_RENDER_STATE);

        if (!renderState.contains(NBT_RENDER_PROPERTIES, Tag.TAG_COMPOUND)) {
            return out;
        }

        CompoundTag properties = renderState.getCompound(NBT_RENDER_PROPERTIES);

        if (!properties.contains(NBT_RENDER_TRANSFORM_UP, Tag.TAG_STRING)) {
            return out;
        }

        NbtUtil.copyStringIfPresent(renderState, out, NBT_RENDER_STATE_NAME);

        CompoundTag outProperties = new CompoundTag();
        NbtUtil.copyStringIfPresent(properties, outProperties, NBT_RENDER_TRANSFORM_UP);

        if (!outProperties.isEmpty()) {
            out.put(NBT_RENDER_PROPERTIES, outProperties);
        }

        return out;
    }

    private static boolean hasStoredGregTransformerState(CompoundTag machineData) {
        if (machineData == null || machineData.isEmpty()) {
            return false;
        }

        if (machineData.contains(NBT_TRANSFORM_UP, Tag.TAG_BYTE)) {
            return true;
        }

        if (!machineData.contains(NBT_RENDER_STATE, Tag.TAG_COMPOUND)) {
            return false;
        }

        CompoundTag renderState = machineData.getCompound(NBT_RENDER_STATE);

        if (!renderState.contains(NBT_RENDER_PROPERTIES, Tag.TAG_COMPOUND)) {
            return false;
        }

        CompoundTag properties = renderState.getCompound(NBT_RENDER_PROPERTIES);

        return properties.contains(NBT_RENDER_TRANSFORM_UP, Tag.TAG_STRING);
    }

    private static boolean readStoredTransformUp(
            CompoundTag machineData,
            boolean fallback
    ) {
        if (machineData.contains(NBT_TRANSFORM_UP, Tag.TAG_BYTE)) {
            return machineData.getBoolean(NBT_TRANSFORM_UP);
        }

        if (!machineData.contains(NBT_RENDER_STATE, Tag.TAG_COMPOUND)) {
            return fallback;
        }

        CompoundTag renderState = machineData.getCompound(NBT_RENDER_STATE);

        if (!renderState.contains(NBT_RENDER_PROPERTIES, Tag.TAG_COMPOUND)) {
            return fallback;
        }

        CompoundTag properties = renderState.getCompound(NBT_RENDER_PROPERTIES);

        if (!properties.contains(NBT_RENDER_TRANSFORM_UP, Tag.TAG_STRING)) {
            return fallback;
        }

        return Boolean.parseBoolean(properties.getString(NBT_RENDER_TRANSFORM_UP));
    }

    private static boolean isWorldAcceleratorTag(@Nullable CompoundTag tag) {
        if (tag == null) {
            return false;
        }

        String id = getMachineId(tag).toLowerCase(Locale.ROOT);

        if (id.contains("world_accelerator")) {
            return true;
        }

        return tag.contains(NBT_IS_RANDOM_TICK_MODE, Tag.TAG_BYTE);
    }

    private static boolean isEnergyConverterTag(@Nullable CompoundTag tag) {
        if (tag == null) {
            return false;
        }

        String id = getMachineId(tag).toLowerCase(Locale.ROOT);

        if (id.contains("energy_converter")) {
            return true;
        }

        if (!tag.contains(NBT_ENERGY_CONTAINER, Tag.TAG_COMPOUND)) {
            return false;
        }

        return tag.getCompound(NBT_ENERGY_CONTAINER).contains(NBT_FE_TO_EU, Tag.TAG_BYTE);
    }

    private static CompoundTag extractRenderStateWithProperty(CompoundTag from, String propertyKey) {
        CompoundTag out = new CompoundTag();

        if (!from.contains(NBT_RENDER_STATE, Tag.TAG_COMPOUND)) {
            return out;
        }

        CompoundTag renderState = from.getCompound(NBT_RENDER_STATE);

        if (!renderState.contains(NBT_RENDER_PROPERTIES, Tag.TAG_COMPOUND)) {
            return out;
        }

        CompoundTag properties = renderState.getCompound(NBT_RENDER_PROPERTIES);

        if (!properties.contains(propertyKey, Tag.TAG_STRING)) {
            return out;
        }

        NbtUtil.copyStringIfPresent(renderState, out, NBT_RENDER_STATE_NAME);

        CompoundTag outProperties = new CompoundTag();
        NbtUtil.copyStringIfPresent(properties, outProperties, propertyKey);

        if (!outProperties.isEmpty()) {
            out.put(NBT_RENDER_PROPERTIES, outProperties);
        }

        return out;
    }

    private static void copyWorldAcceleratorState(CompoundTag from, CompoundTag to) {
        NbtUtil.copyByteIfPresent(from, to, NBT_IS_RANDOM_TICK_MODE);

        CompoundTag renderState = extractRenderStateWithProperty(from, NBT_RENDER_RANDOM_TICK_MODE);

        if (!renderState.isEmpty()) {
            to.put(NBT_RENDER_STATE, renderState);
        }
    }

    private static void copyEnergyConverterState(CompoundTag from, CompoundTag to) {
        if (from.contains(NBT_ENERGY_CONTAINER, Tag.TAG_COMPOUND)) {
            CompoundTag ec = from.getCompound(NBT_ENERGY_CONTAINER);

            if (ec.contains(NBT_FE_TO_EU, Tag.TAG_BYTE)) {
                CompoundTag savedEc = to.contains(NBT_ENERGY_CONTAINER, Tag.TAG_COMPOUND)
                        ? to.getCompound(NBT_ENERGY_CONTAINER).copy()
                        : new CompoundTag();

                savedEc.putBoolean(NBT_FE_TO_EU, ec.getBoolean(NBT_FE_TO_EU));
                to.put(NBT_ENERGY_CONTAINER, savedEc);
            }
        }

        CompoundTag renderState = extractRenderStateWithProperty(from, NBT_RENDER_FE_TO_EU);

        if (!renderState.isEmpty()) {
            to.put(NBT_RENDER_STATE, renderState);
        }
    }

    private static boolean hasStoredWorldAcceleratorMode(CompoundTag machineData) {
        if (machineData == null || machineData.isEmpty()) {
            return false;
        }

        if (machineData.contains(NBT_IS_RANDOM_TICK_MODE, Tag.TAG_BYTE)) {
            return true;
        }

        if (!machineData.contains(NBT_RENDER_STATE, Tag.TAG_COMPOUND)) {
            return false;
        }

        CompoundTag renderState = machineData.getCompound(NBT_RENDER_STATE);

        if (!renderState.contains(NBT_RENDER_PROPERTIES, Tag.TAG_COMPOUND)) {
            return false;
        }

        return renderState.getCompound(NBT_RENDER_PROPERTIES).contains(NBT_RENDER_RANDOM_TICK_MODE, Tag.TAG_STRING);
    }

    private static boolean hasStoredEnergyConverterDirection(CompoundTag machineData) {
        if (machineData == null || machineData.isEmpty()) {
            return false;
        }

        if (machineData.contains(NBT_ENERGY_CONTAINER, Tag.TAG_COMPOUND)
                && machineData.getCompound(NBT_ENERGY_CONTAINER).contains(NBT_FE_TO_EU, Tag.TAG_BYTE)) {
            return true;
        }

        if (!machineData.contains(NBT_RENDER_STATE, Tag.TAG_COMPOUND)) {
            return false;
        }

        CompoundTag renderState = machineData.getCompound(NBT_RENDER_STATE);

        if (!renderState.contains(NBT_RENDER_PROPERTIES, Tag.TAG_COMPOUND)) {
            return false;
        }

        return renderState.getCompound(NBT_RENDER_PROPERTIES).contains(NBT_RENDER_FE_TO_EU, Tag.TAG_STRING);
    }

    private static boolean readStoredRandomTickMode(CompoundTag machineData, boolean fallback) {
        if (machineData.contains(NBT_IS_RANDOM_TICK_MODE, Tag.TAG_BYTE)) {
            return machineData.getBoolean(NBT_IS_RANDOM_TICK_MODE);
        }

        if (!machineData.contains(NBT_RENDER_STATE, Tag.TAG_COMPOUND)) {
            return fallback;
        }

        CompoundTag renderState = machineData.getCompound(NBT_RENDER_STATE);

        if (!renderState.contains(NBT_RENDER_PROPERTIES, Tag.TAG_COMPOUND)) {
            return fallback;
        }

        CompoundTag properties = renderState.getCompound(NBT_RENDER_PROPERTIES);

        if (!properties.contains(NBT_RENDER_RANDOM_TICK_MODE, Tag.TAG_STRING)) {
            return fallback;
        }

        return Boolean.parseBoolean(properties.getString(NBT_RENDER_RANDOM_TICK_MODE));
    }

    private static boolean readStoredFeToEu(CompoundTag machineData, boolean fallback) {
        if (machineData.contains(NBT_ENERGY_CONTAINER, Tag.TAG_COMPOUND)) {
            CompoundTag ec = machineData.getCompound(NBT_ENERGY_CONTAINER);

            if (ec.contains(NBT_FE_TO_EU, Tag.TAG_BYTE)) {
                return ec.getBoolean(NBT_FE_TO_EU);
            }
        }

        if (!machineData.contains(NBT_RENDER_STATE, Tag.TAG_COMPOUND)) {
            return fallback;
        }

        CompoundTag renderState = machineData.getCompound(NBT_RENDER_STATE);

        if (!renderState.contains(NBT_RENDER_PROPERTIES, Tag.TAG_COMPOUND)) {
            return fallback;
        }

        CompoundTag properties = renderState.getCompound(NBT_RENDER_PROPERTIES);

        if (!properties.contains(NBT_RENDER_FE_TO_EU, Tag.TAG_STRING)) {
            return fallback;
        }

        return Boolean.parseBoolean(properties.getString(NBT_RENDER_FE_TO_EU));
    }

    private static void scheduleSingleWorldAcceleratorModeRefresh(
            ServerLevel level,
            BlockPos pos,
            CompoundTag machineData
    ) {
        if (!hasStoredWorldAcceleratorMode(machineData)) {
            return;
        }

        if (isPostPlacementAlreadyPending(level, pos, PendingMode.WORLD_ACCELERATOR_MODE)) {
            return;
        }

        ensureRegistered();

        List<PendingBlockInit> blocks = new ArrayList<>();
        blocks.add(new PendingBlockInit(
                pos.immutable(),
                PendingMode.WORLD_ACCELERATOR_MODE,
                machineData.copy(),
                null
        ));

        PENDING.add(new PendingInit(
                level,
                blocks,
                level.getGameTime() + NEXT_TICK_DELAY
        ));
    }

    private static void scheduleSingleEnergyConverterDirectionRefresh(
            ServerLevel level,
            BlockPos pos,
            CompoundTag machineData
    ) {
        if (!hasStoredEnergyConverterDirection(machineData)) {
            return;
        }

        if (isPostPlacementAlreadyPending(level, pos, PendingMode.ENERGY_CONVERTER_DIRECTION)) {
            return;
        }

        ensureRegistered();

        List<PendingBlockInit> blocks = new ArrayList<>();
        blocks.add(new PendingBlockInit(
                pos.immutable(),
                PendingMode.ENERGY_CONVERTER_DIRECTION,
                machineData.copy(),
                null
        ));

        PENDING.add(new PendingInit(
                level,
                blocks,
                level.getGameTime() + NEXT_TICK_DELAY
        ));
    }

    private static boolean refreshWorldAcceleratorMode(
            ServerLevel level,
            BlockPos pos,
            BlockEntity blockEntity,
            CompoundTag machineData
    ) {
        if (!(blockEntity instanceof MetaMachineBlockEntity)) {
            return false;
        }

        CompoundTag currentTag = saveCurrentTag(blockEntity);

        if (currentTag == null) {
            return false;
        }

        if (!getMachineId(currentTag).toLowerCase(Locale.ROOT).contains("world_accelerator")) {
            return false;
        }

        boolean currentMode = currentTag.getBoolean(NBT_IS_RANDOM_TICK_MODE);
        boolean desiredMode = readStoredRandomTickMode(machineData, currentMode);

        if (currentMode == desiredMode) {
            return false;
        }

        currentTag.putBoolean(NBT_IS_RANDOM_TICK_MODE, desiredMode);
        currentTag.putInt("x", pos.getX());
        currentTag.putInt("y", pos.getY());
        currentTag.putInt("z", pos.getZ());

        try {
            blockEntity.load(currentTag);
            blockEntity.clearRemoved();
            blockEntity.setChanged();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean refreshEnergyConverterDirection(
            ServerLevel level,
            BlockPos pos,
            BlockEntity blockEntity,
            CompoundTag machineData
    ) {
        if (!(blockEntity instanceof MetaMachineBlockEntity)) {
            return false;
        }

        CompoundTag currentTag = saveCurrentTag(blockEntity);

        if (currentTag == null) {
            return false;
        }

        if (!getMachineId(currentTag).toLowerCase(Locale.ROOT).contains("energy_converter")) {
            return false;
        }

        CompoundTag currentEc = currentTag.contains(NBT_ENERGY_CONTAINER, Tag.TAG_COMPOUND)
                ? currentTag.getCompound(NBT_ENERGY_CONTAINER).copy()
                : new CompoundTag();

        boolean currentFeToEu = currentEc.getBoolean(NBT_FE_TO_EU);
        boolean desiredFeToEu = readStoredFeToEu(machineData, currentFeToEu);

        if (currentFeToEu == desiredFeToEu) {
            return false;
        }

        currentEc.putBoolean(NBT_FE_TO_EU, desiredFeToEu);
        currentTag.put(NBT_ENERGY_CONTAINER, currentEc);
        currentTag.putInt("x", pos.getX());
        currentTag.putInt("y", pos.getY());
        currentTag.putInt("z", pos.getZ());

        try {
            blockEntity.load(currentTag);
            blockEntity.clearRemoved();
            blockEntity.setChanged();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static CompoundTag createPostPlacementPipeTag(
            @Nullable BlockEntity be,
            @Nullable CompoundTag currentTag,
            CompoundTag gregMeta
    ) {
        String id = gregMeta.getString(NBT_ID);

        if ((id == null || id.isBlank()) && currentTag != null) {
            id = currentTag.getString(NBT_ID);
        }

        if ((id == null || id.isBlank()) && be != null) {
            try {
                id = be.saveWithFullMetadata().getString(NBT_ID);
            } catch (Throwable ignored) {
                id = "";
            }
        }

        if (id == null || id.isBlank() || !isGregPipeId(id)) {
            return new CompoundTag();
        }

        CompoundTag rawIdTag = new CompoundTag();
        rawIdTag.putString(NBT_ID, id);

        CompoundTag pipeData = gregMeta.getCompound(StructureToolKeys.CLONE_KEY_GREG_PIPE);
        CompoundTag coverData = getPlacedCoverDataForPostInit(currentTag, gregMeta);

        return createWhitelistedGregPipeTag(rawIdTag, pipeData, coverData);
    }

    private static CompoundTag getPlacedCoverDataForPostInit(
            @Nullable CompoundTag currentTag,
            CompoundTag gregMeta
    ) {
        if (currentTag != null && currentTag.contains(NBT_COVER, Tag.TAG_COMPOUND)) {
            return currentTag.getCompound(NBT_COVER).copy();
        }

        return gregMeta.getCompound(StructureToolKeys.CLONE_KEY_GREG_COVER).copy();
    }

    @Nullable
    private static CompoundTag createCoverSnapshotForGuard(@Nullable CompoundTag currentTag) {
        if (currentTag == null) {
            return null;
        }

        return copyCoverOrEmpty(currentTag);
    }

    private static CompoundTag copyCoverOrEmpty(CompoundTag tag) {
        if (!tag.contains(NBT_COVER, Tag.TAG_COMPOUND)) {
            return new CompoundTag();
        }

        return tag.getCompound(NBT_COVER).copy();
    }

    private static boolean hasCoverChangedSinceQueued(
            @Nullable BlockEntity blockEntity,
            @Nullable CompoundTag observedCoverTag
    ) {
        if (blockEntity == null || observedCoverTag == null) {
            return false;
        }

        CompoundTag currentTag = saveCurrentTag(blockEntity);

        if (currentTag == null) {
            return false;
        }

        CompoundTag currentCover = copyCoverOrEmpty(currentTag);

        return !currentCover.equals(observedCoverTag);
    }

    private static CompoundTag getGregMetadata(@Nullable CompoundTag blockMetadata) {
        if (blockMetadata == null) {
            return new CompoundTag();
        }

        if (!blockMetadata.contains(StructureToolKeys.CLONE_KEY_GREG, Tag.TAG_COMPOUND)) {
            return new CompoundTag();
        }

        return blockMetadata.getCompound(StructureToolKeys.CLONE_KEY_GREG);
    }

    private static boolean isGregBlockEntityTag(@Nullable CompoundTag tag) {
        if (tag == null) {
            return false;
        }

        String id = tag.getString(NBT_ID);

        if (!id.isBlank() && id.startsWith(StructureToolKeys.GTCEU_ID_PREFIX)) {
            return true;
        }

        if (StructureToolKeys.GT_CABLE_ID.equals(id)
                || StructureToolKeys.GT_ITEM_PIPE_ID.equals(id)
                || StructureToolKeys.GT_FLUID_PIPE_ID.equals(id)) {
            return true;
        }

        if (tag.contains("connections", Tag.TAG_INT)
                && tag.contains("blockedConnections", Tag.TAG_INT)
                && tag.contains("frameMaterial", Tag.TAG_STRING)) {
            return true;
        }

        return isLikelyGregMachineTag(tag);
    }

    private static boolean isLikelyGregMachineTag(CompoundTag tag) {
        String id = tag.getString(NBT_ID);

        if (id.isBlank()) {
            return false;
        }

        if (id.startsWith("minecraft:")) {
            return false;
        }

        if (isGregPipeId(id)) {
            return false;
        }

        return tag.contains(NBT_COVER, Tag.TAG_COMPOUND)
                || tag.contains("recipeLogic", Tag.TAG_COMPOUND)
                || tag.contains("voidingMode", Tag.TAG_STRING)
                || tag.contains("workingMode", Tag.TAG_STRING)
                || tag.contains("ownerUUID", Tag.TAG_INT_ARRAY)
                || tag.contains("paintingColor", Tag.TAG_INT)
                || tag.contains("durationMultiplier", Tag.TAG_ANY_NUMERIC)
                || tag.contains("renderState", Tag.TAG_COMPOUND)
                || tag.contains("circuitInventory", Tag.TAG_COMPOUND)
                || tag.contains(NBT_BUFFER_POS, Tag.TAG_COMPOUND)
                || tag.contains(NBT_MY_BUFFER_POS, Tag.TAG_COMPOUND)
                || tag.contains("outputFacingItems")
                || tag.contains("outputFacingFluids")
                || tag.contains("inputFacingItems")
                || tag.contains("inputFacingFluids")
                || isPatternBufferTag(tag)
                || isPatternBufferProxyTag(tag);
    }

    private static boolean isGregPipeTag(@Nullable CompoundTag tag) {
        if (tag == null) {
            return false;
        }

        return isGregPipeId(tag.getString(NBT_ID));
    }

    private static boolean isGregPipeId(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }

        if (StructureToolKeys.GT_FLUID_PIPE_ID.equals(id)
                || StructureToolKeys.GT_ITEM_PIPE_ID.equals(id)
                || StructureToolKeys.GT_CABLE_ID.equals(id)) {
            return true;
        }

        if (id.startsWith(StructureToolKeys.GTCEU_ID_PREFIX)) {
            String path = id.substring(StructureToolKeys.GTCEU_ID_PREFIX.length());
            return path.endsWith("_pipe") || path.equals("cable") || path.endsWith("_cable");
        }

        return false;
    }

    private static boolean isGregMachineTag(@Nullable CompoundTag tag) {
        if (tag == null) {
            return false;
        }

        String id = tag.getString(NBT_ID);

        return !id.isBlank()
                && !isGregPipeId(id)
                && (
                id.startsWith(StructureToolKeys.GTCEU_ID_PREFIX)
                        || isLikelyGregMachineTag(tag)
        );
    }

    private static ItemStack normalizeSingle(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack copy = stack.copy();

        copy.setCount(1);
        copy.setTag(null);

        return copy;
    }

    private static void addBaseBlockRequirement(
            ServerLevel level,
            BlockPos pos,
            AbstractStructureCaptureToolItem.RequirementSink requirements
    ) {
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(pos),
                Direction.UP,
                pos,
                false
        );

        ItemStack picked = level.getBlockState(pos).getCloneItemStack(hit, level, pos, null);

        if (!picked.isEmpty()) {
            requirements.add(picked);
            return;
        }

        Item item = level.getBlockState(pos).getBlock().asItem();

        if (item != Items.AIR) {
            requirements.add(new ItemStack(item));
        }
    }

    private static void scheduleSinglePipePostPlacementInit(
            ServerLevel level,
            BlockPos pos,
            CompoundTag blockEntityTag,
            @Nullable CompoundTag observedCoverTag
    ) {
        if (!isGregPipeTag(blockEntityTag)) {
            return;
        }

        if (isPostPlacementAlreadyPending(level, pos, PendingMode.PIPE_LOAD)) {
            return;
        }

        ensureRegistered();

        List<PendingBlockInit> blocks = new ArrayList<>();
        blocks.add(new PendingBlockInit(
                pos.immutable(),
                PendingMode.PIPE_LOAD,
                blockEntityTag.copy(),
                observedCoverTag == null ? null : observedCoverTag.copy()
        ));

        PENDING.add(new PendingInit(
                level,
                blocks,
                level.getGameTime() + NEXT_TICK_DELAY
        ));
    }

    private static void scheduleSingleDataStickApply(
            ServerLevel level,
            BlockPos pos,
            CompoundTag dataStickTag
    ) {
        if (dataStickTag.isEmpty()) {
            return;
        }

        if (isPostPlacementAlreadyPending(level, pos, PendingMode.DATA_STICK_ONLY)) {
            return;
        }

        ensureRegistered();

        List<PendingBlockInit> blocks = new ArrayList<>();
        blocks.add(new PendingBlockInit(
                pos.immutable(),
                PendingMode.DATA_STICK_ONLY,
                dataStickTag.copy(),
                null
        ));

        PENDING.add(new PendingInit(
                level,
                blocks,
                level.getGameTime() + NEXT_TICK_DELAY
        ));
    }

    private static void scheduleSingleTransformerStateRefresh(
            ServerLevel level,
            BlockPos pos,
            CompoundTag machineData
    ) {
        if (!hasStoredGregTransformerState(machineData)) {
            return;
        }

        if (isPostPlacementAlreadyPending(level, pos, PendingMode.TRANSFORMER_STATE)) {
            return;
        }

        ensureRegistered();

        List<PendingBlockInit> blocks = new ArrayList<>();
        blocks.add(new PendingBlockInit(
                pos.immutable(),
                PendingMode.TRANSFORMER_STATE,
                machineData.copy(),
                null
        ));

        PENDING.add(new PendingInit(
                level,
                blocks,
                level.getGameTime() + NEXT_TICK_DELAY
        ));
    }

    private static void scheduleSinglePatternBufferLinkRefresh(
            ServerLevel level,
            BlockPos pos,
            CompoundTag machineData
    ) {
        if (!machineData.contains(NBT_BUFFER_POS, Tag.TAG_COMPOUND)) {
            return;
        }

        if (!machineData.contains(NBT_PATTERN_BUFFER_ID, Tag.TAG_STRING)) {
            return;
        }

        if (isPostPlacementAlreadyPending(level, pos, PendingMode.PATTERN_BUFFER_LINK)) {
            return;
        }

        ensureRegistered();

        List<PendingBlockInit> blocks = new ArrayList<>();
        blocks.add(new PendingBlockInit(
                pos.immutable(),
                PendingMode.PATTERN_BUFFER_LINK,
                machineData.copy(),
                null
        ));

        PENDING.add(new PendingInit(
                level,
                blocks,
                level.getGameTime() + NEXT_TICK_DELAY
        ));
    }

    private static void scheduleSingleMachineCoverReinit(
            ServerLevel level,
            BlockPos pos
    ) {
        if (isPostPlacementAlreadyPending(level, pos, PendingMode.MACHINE_COVER_REINIT)) {
            return;
        }

        ensureRegistered();

        List<PendingBlockInit> blocks = new ArrayList<>();
        blocks.add(new PendingBlockInit(
                pos.immutable(),
                PendingMode.MACHINE_COVER_REINIT,
                new CompoundTag(),
                null
        ));

        PENDING.add(new PendingInit(
                level,
                blocks,
                level.getGameTime() + NEXT_TICK_DELAY
        ));
    }

    private static boolean hasAnyCover(@Nullable CompoundTag coverTag) {
        return coverTag != null && !coverTag.isEmpty();
    }

    private static void ensureRegistered() {
        if (!registered) {
            MinecraftForge.EVENT_BUS.register(GTCEuStructureExtension.class);
            registered = true;
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PENDING.removeIf(pending -> pending.level == level);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (!(event.level instanceof ServerLevel level)) {
            return;
        }

        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        long now = level.getGameTime();

        List<PendingInit> toProcess = new ArrayList<>();

        PENDING.removeIf(pending -> {
            if (pending.level != level) {
                return false;
            }

            if (now < pending.runAtGameTime) {
                return false;
            }

            toProcess.add(pending);
            return true;
        });

        for (PendingInit pending : toProcess) {
            runPostPlacementInit(level, pending.blocks);
        }
    }

    private static void runPostPlacementInit(ServerLevel level, List<PendingBlockInit> blocks) {
        List<BlockPos> refreshedPositions = new ArrayList<>();
        List<PendingBlockInit> reconnectBlocks = new ArrayList<>();

        for (PendingBlockInit pendingBlock : blocks) {
            BlockPos worldPos = pendingBlock.pos();
            BlockEntity blockEntity = level.getBlockEntity(worldPos);

            if (blockEntity == null) {
                continue;
            }

            if (pendingBlock.mode() == PendingMode.PIPE_LOAD) {
                CompoundTag blockEntityTag = pendingBlock.payload();

                if (!isGregPipeTag(blockEntityTag)) {
                    continue;
                }

                if (!(blockEntity instanceof PipeBlockEntity<?, ?> pipe)) {
                    continue;
                }

                if (hasCoverChangedSinceQueued(blockEntity, pendingBlock.observedCoverTag())) {
                    continue;
                }

                initSinglePipe(level, worldPos, pipe, blockEntityTag);
                refreshedPositions.add(worldPos);

                if (!isPostPlacementAlreadyPending(level, worldPos, PendingMode.PIPE_RECONNECT)) {
                    reconnectBlocks.add(new PendingBlockInit(
                            worldPos,
                            PendingMode.PIPE_RECONNECT,
                            blockEntityTag.copy(),
                            null
                    ));
                }

                continue;
            }

            if (pendingBlock.mode() == PendingMode.PIPE_RECONNECT) {
                if (!(blockEntity instanceof PipeBlockEntity<?, ?> pipe)) {
                    continue;
                }

                reconnectPipe(level, worldPos, pipe, pendingBlock.payload());
                refreshedPositions.add(worldPos);
                continue;
            }

            if (pendingBlock.mode() == PendingMode.TRANSFORMER_STATE) {
                boolean changed = refreshTransformerRuntimeState(
                        level,
                        worldPos,
                        blockEntity,
                        pendingBlock.payload()
                );

                if (changed) {
                    syncGenericGregBlockEntityNoLoad(level, worldPos, blockEntity);
                    refreshedPositions.add(worldPos);
                }

                continue;
            }

            if (pendingBlock.mode() == PendingMode.DATA_STICK_ONLY) {
                boolean changed = applyDataStickTag(level, blockEntity, pendingBlock.payload());

                if (changed) {
                    syncGenericGregBlockEntityNoLoad(level, worldPos, blockEntity);
                    refreshedPositions.add(worldPos);
                }

                continue;
            }

            if (pendingBlock.mode() == PendingMode.PATTERN_BUFFER_LINK) {
                boolean changed = applyPatternBufferLink(
                        level,
                        worldPos,
                        blockEntity,
                        pendingBlock.payload()
                );

                if (changed) {
                    refreshedPositions.add(worldPos);
                }

                continue;
            }

            if (pendingBlock.mode() == PendingMode.WORLD_ACCELERATOR_MODE) {
                boolean changed = refreshWorldAcceleratorMode(
                        level,
                        worldPos,
                        blockEntity,
                        pendingBlock.payload()
                );

                if (changed) {
                    syncGenericGregBlockEntityNoLoad(level, worldPos, blockEntity);
                    refreshedPositions.add(worldPos);
                }

                continue;
            }

            if (pendingBlock.mode() == PendingMode.ENERGY_CONVERTER_DIRECTION) {
                boolean changed = refreshEnergyConverterDirection(
                        level,
                        worldPos,
                        blockEntity,
                        pendingBlock.payload()
                );

                if (changed) {
                    syncGenericGregBlockEntityNoLoad(level, worldPos, blockEntity);
                    refreshedPositions.add(worldPos);
                }

                continue;
            }

            if (pendingBlock.mode() == PendingMode.MACHINE_COVER_REINIT) {
                boolean changed = reinitMachineCoversDeferred(blockEntity);

                if (changed) {
                    syncGenericGregBlockEntityNoLoad(level, worldPos, blockEntity);
                    refreshedPositions.add(worldPos);
                }

                continue;
            }

            if (pendingBlock.mode() == PendingMode.MULTIBLOCK_REVALIDATE) {
                reformMultiblock(level, blockEntity);
            }
        }

        for (BlockPos pos : refreshedPositions) {
            notifyPostPlacedNeighborhood(level, pos);
        }

        if (!reconnectBlocks.isEmpty()) {
            ensureRegistered();
            PENDING.add(new PendingInit(
                    level,
                    reconnectBlocks,
                    level.getGameTime() + NEXT_TICK_DELAY
            ));
        }
    }

    private static void reconnectPipe(
            ServerLevel level,
            BlockPos pos,
            PipeBlockEntity<?, ?> pipe,
            CompoundTag originalTag
    ) {
        int connections = pipe.getConnections();

        if (connections == 0) {
            connections = originalTag.getInt("connections");
        }

        for (Direction side : Direction.values()) {
            if (!PipeBlockEntity.isConnected(connections, side)) {
                continue;
            }

            try {
                pipe.setConnection(side, false, false);
            } catch (Throwable ignored) {
            }

            try {
                pipe.setConnection(side, true, false);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void initSinglePipe(
            ServerLevel level,
            BlockPos pos,
            PipeBlockEntity<?, ?> pipe,
            CompoundTag originalTag
    ) {
        CompoundTag tag = originalTag.copy();

        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());

        pipe.load(tag);
        pipe.clearRemoved();

        try {
            pipe.onLoad();
        } catch (Throwable ignored) {
        }

        PipeCoverContainer coverContainer = pipe.getCoverContainer();

        coverContainer.onLoad();

        for (Direction side : Direction.values()) {
            CoverBehavior cover = coverContainer.getCoverAtSide(side);

            if (cover == null) {
                continue;
            }

            coverContainer.setCoverAtSide(cover, side);
            cover.onLoad();
            cover.getSyncStorage().markAllDirty();
        }

        pipe.getSyncStorage().markAllDirty();
        coverContainer.getSyncStorage().markAllDirty();

        coverContainer.scheduleNeighborShapeUpdate();
        coverContainer.notifyBlockUpdate();
        coverContainer.scheduleRenderUpdate();
        coverContainer.markDirty();

        pipe.notifyBlockUpdate();
        pipe.scheduleRenderUpdate();
        pipe.onChanged();
        pipe.setChanged();

        BlockState state = level.getBlockState(pos);

        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        level.getChunkSource().blockChanged(pos);

        for (Direction side : Direction.values()) {
            BlockPos neighborPos = pos.relative(side);
            BlockState neighborState = level.getBlockState(neighborPos);

            level.sendBlockUpdated(neighborPos, neighborState, neighborState, Block.UPDATE_ALL);
        }

        ClientboundBlockEntityDataPacket packet = ClientboundBlockEntityDataPacket.create(pipe);

        for (ServerPlayer player : level.players()) {
            player.connection.send(packet);
        }
    }

    private static boolean refreshTransformerRuntimeState(
            ServerLevel level,
            BlockPos pos,
            BlockEntity blockEntity,
            CompoundTag machineData
    ) {
        if (!(blockEntity instanceof MetaMachineBlockEntity mmbe)) {
            return false;
        }

        if (!(mmbe.getMetaMachine() instanceof TransformerMachine transformer)) {
            return false;
        }

        boolean desiredTransformUp = readStoredTransformUp(
                machineData,
                transformer.isTransformUp()
        );

        try {
            if (transformer.isTransformUp() != desiredTransformUp) {
                transformer.setTransformUp(desiredTransformUp);
            } else {
                transformer.updateEnergyContainer(desiredTransformUp);
            }

            try {
                transformer.getSyncStorage().markAllDirty();
            } catch (Throwable ignored) {
            }

            try {
                mmbe.getSyncStorage().markAllDirty();
            } catch (Throwable ignored) {
            }

            try {
                transformer.notifyBlockUpdate();
            } catch (Throwable ignored) {
            }

            try {
                transformer.scheduleRenderUpdate();
            } catch (Throwable ignored) {
            }

            try {
                transformer.markDirty();
            } catch (Throwable ignored) {
            }

            blockEntity.setChanged();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean applyDataStickTag(
            ServerLevel level,
            BlockEntity blockEntity,
            CompoundTag dataStickTag
    ) {
        if (dataStickTag.isEmpty()) {
            return false;
        }

        if (!(blockEntity instanceof MetaMachineBlockEntity mmbe)) {
            return false;
        }

        if (!(mmbe.getMetaMachine() instanceof IDataStickInteractable interactable)) {
            return false;
        }

        ItemStack dataStick = new ItemStack(Items.STICK);
        dataStick.setTag(dataStickTag.copy());

        try {
            InteractionResult result = interactable.onDataStickUse(
                    FakePlayerFactory.getMinecraft(level),
                    dataStick
            );

            if (result.consumesAction() || result == InteractionResult.SUCCESS) {
                blockEntity.setChanged();
                return true;
            }
        } catch (Throwable ignored) {
        }

        return false;
    }

    private static void syncGenericGregBlockEntityNoLoad(
            ServerLevel level,
            BlockPos pos,
            BlockEntity blockEntity
    ) {
        if (blockEntity instanceof MetaMachineBlockEntity mmbe) {
            try {
                mmbe.getSyncStorage().markAllDirty();
            } catch (Throwable ignored) {
            }

            try {
                mmbe.getMetaMachine().getSyncStorage().markAllDirty();
            } catch (Throwable ignored) {
            }

            try {
                mmbe.getMetaMachine().notifyBlockUpdate();
            } catch (Throwable ignored) {
            }

            try {
                mmbe.getMetaMachine().scheduleRenderUpdate();
            } catch (Throwable ignored) {
            }

            try {
                mmbe.getMetaMachine().markDirty();
            } catch (Throwable ignored) {
            }
        }

        if (isAe2GridConnectedGregMachine(blockEntity)) {
            refreshAe2GridNodeIfPresent(level, pos, blockEntity);
        }

        try {
            blockEntity.setChanged();
        } catch (Throwable ignored) {
        }

        BlockState state = level.getBlockState(pos);

        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        level.getChunkSource().blockChanged(pos);

        ClientboundBlockEntityDataPacket packet = ClientboundBlockEntityDataPacket.create(blockEntity);

        for (ServerPlayer player : level.players()) {
            player.connection.send(packet);
        }
    }

    private static void notifyPostPlacedNeighborhood(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        try {
            level.updateNeighborsAt(pos, state.getBlock());
        } catch (Throwable ignored) {
        }

        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        level.getChunkSource().blockChanged(pos);

        for (Direction side : Direction.values()) {
            BlockPos neighborPos = pos.relative(side);
            BlockState neighborState = level.getBlockState(neighborPos);

            try {
                level.updateNeighborsAt(neighborPos, neighborState.getBlock());
            } catch (Throwable ignored) {
            }

            try {
                neighborState.neighborChanged(
                        level,
                        neighborPos,
                        state.getBlock(),
                        pos,
                        false
                );
            } catch (Throwable ignored) {
            }

            level.sendBlockUpdated(neighborPos, neighborState, neighborState, Block.UPDATE_ALL);
            level.getChunkSource().blockChanged(neighborPos);
        }
    }

    @Override
    public void onTemplatePasted(ServerLevel level, BlockPos placementOrigin, CompoundTag templateTag) {
        List<PendingBlockInit> blocks = new ArrayList<>();
        List<PendingBlockInit> controllers = new ArrayList<>();

        for (TemplateUtil.BlockInfo info : TemplateUtil.parseRawBlocksFromTag(templateTag)) {
            BlockPos worldPos = placementOrigin.offset(info.pos()).immutable();
            BlockEntity blockEntity = level.getBlockEntity(worldPos);

            if (blockEntity == null) {
                continue;
            }

            CompoundTag currentTag = saveCurrentTag(blockEntity);

            if (blockEntity instanceof PipeBlockEntity<?, ?>) {
                if (!isPostPlacementAlreadyPending(level, worldPos, PendingMode.PIPE_LOAD)
                        && isGregPipeTag(currentTag)) {
                    blocks.add(new PendingBlockInit(
                            worldPos,
                            PendingMode.PIPE_LOAD,
                            currentTag.copy(),
                            createCoverSnapshotForGuard(currentTag)
                    ));
                }

                continue;
            }

            if (blockEntity instanceof MetaMachineBlockEntity mmbe) {
                reapplyFluidTankLockFilters(blockEntity);
                reapplyPaintingColor(blockEntity);

                if (mmbe.getMetaMachine() instanceof IMultiController
                        && !isPostPlacementAlreadyPending(level, worldPos, PendingMode.MULTIBLOCK_REVALIDATE)) {
                    controllers.add(new PendingBlockInit(
                            worldPos,
                            PendingMode.MULTIBLOCK_REVALIDATE,
                            new CompoundTag(),
                            null
                    ));
                }

                if (mmbe.getMetaMachine() instanceof TransformerMachine
                        && currentTag != null
                        && hasStoredGregTransformerState(currentTag)
                        && !isPostPlacementAlreadyPending(level, worldPos, PendingMode.TRANSFORMER_STATE)) {
                    blocks.add(new PendingBlockInit(
                            worldPos,
                            PendingMode.TRANSFORMER_STATE,
                            currentTag.copy(),
                            null
                    ));
                }

                if (mmbe.getMetaMachine() instanceof IDataStickInteractable
                        && !isPostPlacementAlreadyPending(level, worldPos, PendingMode.DATA_STICK_ONLY)) {
                    CompoundTag dataStick = collectGregDataStick(blockEntity);

                    if (!dataStick.isEmpty()) {
                        blocks.add(new PendingBlockInit(
                                worldPos,
                                PendingMode.DATA_STICK_ONLY,
                                dataStick,
                                null
                        ));
                    }
                }

                if (currentTag != null
                        && currentTag.contains(NBT_COVER, Tag.TAG_COMPOUND)
                        && hasAnyCover(currentTag.getCompound(NBT_COVER))
                        && !isPostPlacementAlreadyPending(level, worldPos, PendingMode.MACHINE_COVER_REINIT)) {
                    blocks.add(new PendingBlockInit(
                            worldPos,
                            PendingMode.MACHINE_COVER_REINIT,
                            new CompoundTag(),
                            null
                    ));
                }

                if (!isPostPlacementAlreadyPending(level, worldPos, PendingMode.PATTERN_BUFFER_LINK)) {
                    CompoundTag linkData = buildPatternBufferLinkDataFromTemplateTag(level, info.blockEntityTag());

                    if (linkData.contains(NBT_BUFFER_POS, Tag.TAG_COMPOUND)
                            && linkData.contains(NBT_PATTERN_BUFFER_ID, Tag.TAG_STRING)) {
                        blocks.add(new PendingBlockInit(
                                worldPos,
                                PendingMode.PATTERN_BUFFER_LINK,
                                linkData,
                                null
                        ));
                    }
                }
            }
        }

        if (blocks.isEmpty() && controllers.isEmpty()) {
            return;
        }

        ensureRegistered();

        if (!blocks.isEmpty()) {
            PENDING.add(new PendingInit(
                    level,
                    blocks,
                    level.getGameTime() + NEXT_TICK_DELAY
            ));
        }

        if (!controllers.isEmpty()) {
            PENDING.add(new PendingInit(
                    level,
                    controllers,
                    level.getGameTime() + MULTIBLOCK_REFORM_DELAY
            ));
        }
    }

    private static boolean isPostPlacementAlreadyPending(
            ServerLevel level,
            BlockPos pos,
            PendingMode mode
    ) {
        for (PendingInit pending : PENDING) {
            if (pending.level != level) {
                continue;
            }

            for (PendingBlockInit block : pending.blocks) {
                if (block.mode() == mode && block.pos().equals(pos)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Nullable
    private static BlockState getGregFrameState(String frameMaterial) {
        if (frameMaterial == null || frameMaterial.isBlank()) {
            return null;
        }

        String materialPath = frameMaterial;
        int sep = materialPath.indexOf(':');

        if (sep >= 0 && sep + 1 < materialPath.length()) {
            materialPath = materialPath.substring(sep + 1);
        }

        ResourceLocation frameId = new ResourceLocation("gtceu", materialPath + "_frame");
        Block frameBlock = ForgeRegistries.BLOCKS.getValue(frameId);

        if (frameBlock == null || frameBlock == Blocks.AIR) {
            return null;
        }

        return frameBlock.defaultBlockState();
    }

    private static ItemStack getGregFrameItem(String frameMaterial) {
        if (frameMaterial == null || frameMaterial.isBlank()) {
            return ItemStack.EMPTY;
        }

        String materialPath = frameMaterial;
        int sep = materialPath.indexOf(':');

        if (sep >= 0 && sep + 1 < materialPath.length()) {
            materialPath = materialPath.substring(sep + 1);
        }

        ResourceLocation frameId = new ResourceLocation("gtceu", materialPath + "_frame");
        Item frameItem = ForgeRegistries.ITEMS.getValue(frameId);

        if (frameItem == null || frameItem == Items.AIR) {
            return ItemStack.EMPTY;
        }

        return new ItemStack(frameItem);
    }

    private static void collectGregAttachItems(@Nullable Tag tag, java.util.function.Consumer<ItemStack> sink) {
        if (tag == null) {
            return;
        }

        if (tag instanceof CompoundTag compoundTag) {
            collectNamedCostItem(compoundTag, "attachItem", sink);
            collectNamedCostItem(compoundTag, "filterItem", sink);

            for (String key : compoundTag.getAllKeys()) {
                if ("attachItem".equals(key) || "filterItem".equals(key)) {
                    continue;
                }

                if ("matches".equals(key)) {
                    continue;
                }

                collectGregAttachItems(compoundTag.get(key), sink);
            }

            return;
        }

        if (tag instanceof ListTag listTag) {
            for (int i = 0; i < listTag.size(); i++) {
                collectGregAttachItems(listTag.get(i), sink);
            }
        }
    }

    private static void collectNamedCostItem(
            CompoundTag parent,
            String key,
            java.util.function.Consumer<ItemStack> sink
    ) {
        if (!parent.contains(key, Tag.TAG_COMPOUND)) {
            return;
        }

        ItemStack stack = readCostItemStack(parent.getCompound(key));

        if (!stack.isEmpty()) {
            sink.accept(stack);
        }
    }

    private static ItemStack readCostItemStack(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = NbtUtil.tryReadSavedItemStack(tag);

        if (stack.isEmpty()) {
            try {
                stack = ItemStack.of(tag.copy());
            } catch (Throwable ignored) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack cost = stack.copy();

        int amount = Math.max(1, cost.getCount());

        cost.setCount(amount);
        cost.setTag(null);

        return cost;
    }

    public static void scheduleReplacedPipeInit(
            ServerLevel level,
            BlockPos pos,
            @Nullable CompoundTag savedConnectionState
    ) {
        BlockEntity be = level.getBlockEntity(pos);
        CompoundTag currentTag = saveCurrentTag(be);

        if (currentTag == null || !isGregPipeTag(currentTag)) {
            return;
        }

        CompoundTag initTag = currentTag.copy();

        if (savedConnectionState != null) {
            if (savedConnectionState.contains("connections", Tag.TAG_INT)) {
                initTag.putInt("connections", savedConnectionState.getInt("connections"));
            }

            if (savedConnectionState.contains("blockedConnections", Tag.TAG_INT)) {
                initTag.putInt("blockedConnections", savedConnectionState.getInt("blockedConnections"));
            }
        }

        scheduleSinglePipePostPlacementInit(level, pos, initTag, createCoverSnapshotForGuard(currentTag));
    }

    private enum PendingMode {
        PIPE_LOAD,
        PIPE_RECONNECT,
        DATA_STICK_ONLY,
        TRANSFORMER_STATE,
        PATTERN_BUFFER_LINK,
        WORLD_ACCELERATOR_MODE,
        ENERGY_CONVERTER_DIRECTION,
        MACHINE_COVER_REINIT,
        MULTIBLOCK_REVALIDATE
    }

    private record PendingBlockInit(
            BlockPos pos,
            PendingMode mode,
            CompoundTag payload,
            @Nullable CompoundTag observedCoverTag
    ) {
    }

    private static final class PendingInit {
        private final ServerLevel level;
        private final List<PendingBlockInit> blocks;
        private final long runAtGameTime;

        private PendingInit(ServerLevel level, List<PendingBlockInit> blocks, long runAtGameTime) {
            this.level = level;
            this.blocks = blocks;
            this.runAtGameTime = runAtGameTime;
        }
    }

    @Override
    public void onBlockRestored(
            ServerLevel level,
            BlockPos pos,
            @Nullable BlockEntity be,
            @Nullable CompoundTag savedBeTag
    ) {
        scheduleReplacedPipeInit(level, pos, savedBeTag);

        if (be instanceof MetaMachineBlockEntity) {
            CompoundTag currentTag = saveCurrentTag(be);

            if (currentTag != null
                    && currentTag.contains(NBT_COVER, Tag.TAG_COMPOUND)
                    && hasAnyCover(currentTag.getCompound(NBT_COVER))) {
                scheduleSingleMachineCoverReinit(level, pos);
            }
        }
    }

    @Override
    public void onBeforeBlockRemoved(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);

        if (!(be instanceof MetaMachineBlockEntity mmbe)) {
            return;
        }

        if (!(mmbe.getMetaMachine() instanceof IMultiPart part)) {
            return;
        }

        for (IMultiController controller : part.getControllers()) {
            try {
                controller.onStructureInvalid();
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public boolean collectUndoRefunds(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity be,
            List<ItemStack> refunds
    ) {
        CompoundTag currentTag = saveCurrentTag(be);

        if (!handlesRequirements(state, currentTag)
                && !(be instanceof MetaMachineBlockEntity)
                && !(be instanceof PipeBlockEntity<?, ?>)) {
            return false;
        }

        addBaseBlockRefund(level, pos, refunds);

        if (currentTag == null) {
            return true;
        }

        if (currentTag.contains(NBT_COVER, Tag.TAG_COMPOUND)) {
            collectGregCoverRequirements(currentTag.getCompound(NBT_COVER), refunds::add);
        }

        if (isGregPipeTag(currentTag) && currentTag.contains("frameMaterial", Tag.TAG_STRING)) {
            ItemStack frameItem = getGregFrameItem(currentTag.getString("frameMaterial"));

            if (!frameItem.isEmpty()) {
                refunds.add(frameItem);
            }
        }

        return true;
    }

    @Override
    public boolean hasNonEmptyStorage(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity be
    ) {
        if (StructureCloneExtension.super.hasNonEmptyStorage(level, pos, state, be)) {
            return true;
        }
        if (be instanceof MetaMachineBlockEntity mmbe) {
            try {
                for (MachineTrait trait : mmbe.getMetaMachine().getTraits()) {
                    if (trait instanceof NotifiableFluidTank tank && !tank.isEmpty()) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    @Nullable
    private static CompoundTag saveCurrentTag(@Nullable BlockEntity be) {
        if (be == null) {
            return null;
        }

        try {
            return be.saveWithFullMetadata();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void addBaseBlockRefund(
            ServerLevel level,
            BlockPos pos,
            List<ItemStack> refunds
    ) {
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(pos),
                Direction.UP,
                pos,
                false
        );

        ItemStack picked = level.getBlockState(pos).getCloneItemStack(hit, level, pos, null);

        if (!picked.isEmpty()) {
            refunds.add(picked);
            return;
        }

        Item item = level.getBlockState(pos).getBlock().asItem();

        if (item != Items.AIR) {
            refunds.add(new ItemStack(item));
        }
    }

    private static boolean isAe2GridConnectedGregMachine(@Nullable BlockEntity blockEntity) {
        if (!IsModLoaded.AE2) {
            return false;
        }

        return GTCEuAE2PostPasteOps.isAe2GridConnectedMachine(blockEntity);
    }

    private static void refreshAe2GridNodeIfPresent(
            ServerLevel level,
            BlockPos pos,
            BlockEntity blockEntity
    ) {
        if (!IsModLoaded.AE2) {
            return;
        }

        GTCEuAE2PostPasteOps.refreshGridNodeIfPresent(level, pos, blockEntity);
    }
}